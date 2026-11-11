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

    fun compile(body: String, outside: String = "") = KotlinCompilation().apply {
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
                    abstract override suspend fun tryPerform(principal: TestPrincipal, params: Params): Block

                    final override fun wrap(dsl: ChangesDsl) = StrictDsl(dsl)
                }

                class ProbeUow(private val executionContext: ExecutionContext) : StrictFamily<String>(executionContext) {
                    override suspend fun tryPerform(principal: TestPrincipal, params: Params) = $body
                }

                $outside
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
                "TestPrincipal, params: Params): WrappingUnitOfWork.Block<TestPrincipal, Params, String, StrictDsl>'",
        )
        result.errorCount() shouldBe 1
    }

    test("A Block cannot be built outside eva") {
        val result = compile("""Block(noChanges("ok"))""")
        result.shouldRejectWith(
            "Cannot access 'WrappingUnitOfWork<TestPrincipal, Params, String, StrictDsl>.constructor(changes: " +
                "Changes<String>): WrappingUnitOfWork.Block<TestPrincipal, Params, String, StrictDsl>': it is internal",
        )
        result.errorCount() shouldBe 1
    }

    test("The block of a family with another DSL does not compile as the result of a family that returns Block") {
        val result = compile(
            "Raw(executionContext).tryPerform(principal, params)",
            """
            class Raw(executionContext: ExecutionContext) :
                WrappingUnitOfWork<TestPrincipal, Params, String, ChangesDsl>(executionContext) {
                final override fun wrap(dsl: ChangesDsl) = dsl
                override suspend fun tryPerform(principal: TestPrincipal, params: Params): Block =
                    changes { add(createdTestModel("smuggled", 1)).let { "ok" } }
            }
            """.trimIndent(),
        )
        result.shouldRejectWith("is not a subtype of the return type of the overridden member")
        result.errorCount() shouldBe 1
    }

    fun compileExecute(familyMarker: String) = KotlinCompilation().apply {
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
                import com.razz.eva.uow.composable.ComposableUow
                import com.razz.eva.uow.composable.UnitOfWork

                object Params : UowParams<Params>

                class FamilyDsl(val dsl: ChangesDsl)

                abstract class Family<R : Any>(executionContext: ExecutionContext) :
                    WrappingUnitOfWork<TestPrincipal, Params, R, FamilyDsl>(executionContext)$familyMarker {
                    final override fun wrap(dsl: ChangesDsl) = FamilyDsl(dsl)
                }

                class ChildUow(executionContext: ExecutionContext) : Family<String>(executionContext) {
                    override suspend fun tryPerform(principal: TestPrincipal, params: Params) =
                        changes { dsl.add(createdTestModel("child", 1)).let { "child" } }
                }

                class ParentUow(executionContext: ExecutionContext) :
                    UnitOfWork<TestPrincipal, Params, String>(executionContext) {
                    override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                        execute({ ctx -> ChildUow(ctx) }, principal) { Params }
                    }
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

    test("A unit of a family that implements ComposableUow compiles as an execute argument") {
        compileExecute(", ComposableUow").exitCode shouldBe KotlinCompilation.ExitCode.OK
    }

    test("A unit of a family that does not implement ComposableUow does not compile as an execute argument") {
        val result = compileExecute("")
        result.shouldRejectWith("Return type mismatch: expected 'ComposableUow', actual 'ChildUow'.")
        result.errorCount() shouldBe 1
    }
})
