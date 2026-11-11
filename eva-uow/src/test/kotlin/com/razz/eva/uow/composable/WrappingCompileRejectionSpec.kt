@file:OptIn(ExperimentalCompilerApi::class)

package com.razz.eva.uow.composable

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.io.OutputStream

// A family that declares tryPerform as returning Block can be satisfied only by its own changes { }.
class WrappingCompileRejectionSpec : FunSpec({

    fun compile(body: String) = KotlinCompilation().apply {
        sources = listOf(
            SourceFile.kotlin(
                "Probe.kt",
                """
                package probe

                import com.razz.eva.domain.TestModel.Factory.createdTestModel
                import com.razz.eva.uow.ExecutionContext
                import com.razz.eva.uow.TestPrincipal
                import com.razz.eva.uow.UowParams
                import com.razz.eva.uow.WrappingUnitOfWork
                import com.razz.eva.uow.composable.ChangesDsl

                object Params : UowParams<Params>

                class StrictDsl(val dsl: ChangesDsl)

                abstract class StrictFamily<R : Any>(executionContext: ExecutionContext) :
                    WrappingUnitOfWork<TestPrincipal, Params, R, StrictDsl>(executionContext) {
                    abstract override suspend fun tryPerform(principal: TestPrincipal, params: Params): Block<R>

                    override fun wrap(dsl: ChangesDsl) = StrictDsl(dsl)
                }

                class ProbeUow(executionContext: ExecutionContext) : StrictFamily<String>(executionContext) {
                    override suspend fun tryPerform(principal: TestPrincipal, params: Params) = $body
                }
                """.trimIndent(),
            ),
        )
        inheritClassPath = true
        jvmTarget = "21"
        languageVersion = "2.2"
        verbose = false
        messageOutputStream = OutputStream.nullOutputStream()
    }.compile()

    test("The family's own changes { } compiles") {
        val result = compile("""changes { dsl.add(createdTestModel("probe", 1)).let { "ok" } }""")
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
    }

    test("noChanges() does not compile as the result of a family that returns Block") {
        val result = compile("""noChanges("ok")""")
        result.shouldRejectWith(
            "is not a subtype of the return type of the overridden member 'suspend fun tryPerform(principal: " +
                "TestPrincipal, params: Params): WrappingUnitOfWork.Block<String>'",
        )
        result.errorCount() shouldBe 1
    }

    test("A Block cannot be built outside eva") {
        val result = compile("""Block(noChanges("ok"))""")
        result.shouldRejectWith(
            "Cannot access 'constructor<R>(changes: Changes<R>): WrappingUnitOfWork.Block<R>': it is internal",
        )
        result.errorCount() shouldBe 1
    }
})
