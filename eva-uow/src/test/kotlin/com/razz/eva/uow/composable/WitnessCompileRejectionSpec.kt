@file:OptIn(ExperimentalCompilerApi::class)

package com.razz.eva.uow.composable

import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.OutputStream

// Asserts the probe was rejected for the stated reason. Without the jvmTarget below, a probe whose
// body touches an eva inline function fails with "Cannot inline bytecode built with JVM target 21",
// whose text contains the tokens these tests look for: that would be a green run for a broken probe.
fun JvmCompilationResult.shouldRejectWith(diagnostic: String) {
    messages shouldNotContain "Cannot inline bytecode"
    exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
    messages shouldContain diagnostic
}

fun JvmCompilationResult.errorCount() = messages.lines().count { it.startsWith("e: ") }

// The witness is a compile-time guard; these pin, in CI, that the compiler rejects what it must.
class WitnessCompileRejectionSpec : FunSpec({

    fun probe(blockTail: String, outside: String) = SourceFile.kotlin(
        "Probe.kt",
        """
        package probe

import com.razz.eva.domain.Department
        import com.razz.eva.domain.DepartmentId
        import com.razz.eva.domain.Employee
        import com.razz.eva.domain.Employee.Companion.newEmployee
        import com.razz.eva.domain.Name
        import com.razz.eva.domain.Ration.BUBALEH
        import com.razz.eva.uow.ExecutionContext
        import com.razz.eva.uow.TestPrincipal
        import com.razz.eva.uow.UowParams
        import com.razz.eva.uow.composable.UnitOfWork

        data class Params(val employee: Employee, val department: Department<*>) : UowParams<Params>

        class ProbeUow(
            executionContext: ExecutionContext,
        ) : UnitOfWork<TestPrincipal, Params, Employee>(executionContext) {
            override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                $blockTail
            }
        }

        $outside
        """.trimIndent(),
    )

    fun compile(blockTail: String, outside: String = "") = KotlinCompilation().apply {
        sources = listOf(probe(blockTail, outside))
        inheritClassPath = true
        jvmTarget = "21"
        kotlincArguments = listOf("-Xcontext-parameters")
        verbose = false
        messageOutputStream = OutputStream.nullOutputStream()
    }.compile()

    test("A registration lambda supplies the witness; the same call outside any block does not compile") {
        val result = compile(
            "update(params.employee) { changeDepartment(params.department) }",
            "fun outside(employee: Employee, department: Department<*>) = employee.changeDepartment(department)",
        )
        result.shouldRejectWith("No context argument for '_: Witness<EmployeeId>' found.")
        result.errorCount() shouldBe 1
    }

    test("A witnessed mutation inside the block that is not its own registration does not compile") {
        val result = compile(
            """
            val moved = params.employee.changeDepartment(params.department)
            notChanged(params.employee)
            """.trimIndent(),
        )
        result.shouldRejectWith("No context argument for '_: Witness<EmployeeId>' found.")
    }

    test("add { } supplies the witness a factory needs; creation outside it does not compile") {
        val result = compile(
            """add { newEmployee(Name("Grace", "Hopper"), params.department.id(), "g@test.com", BUBALEH) }""",
            """fun outside(id: DepartmentId) = newEmployee(Name("Grace", "Hopper"), id, "g@test.com", BUBALEH)""",
        )
        result.shouldRejectWith("No context argument for '_: Witness<EmployeeId>' found.")
        result.errorCount() shouldBe 1
    }

    // The witness type is inferred from the lambda body, so the stray call fixes it to EmployeeId and the
    // factory's own model no longer fits: one error, reported against the factory rather than the stray.
    test("add { } witnesses one id type; a mutation of another model type inside it does not compile") {
        val result = compile(
            """
            add {
                val moved = params.employee.changeDepartment(params.department)
                com.razz.eva.domain.TestModel.Factory.createdTestModel("probe", 1)
            }
            notChanged(params.employee)
            """.trimIndent(),
        )
        result.shouldRejectWith("Return type mismatch: expected 'Model<EmployeeId, ModelEvent<EmployeeId>>'")
        result.errorCount() shouldBe 1
    }
})
