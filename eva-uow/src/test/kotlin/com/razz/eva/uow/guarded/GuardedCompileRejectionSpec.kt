@file:OptIn(ExperimentalCompilerApi::class)

package com.razz.eva.uow.guarded

import com.razz.eva.uow.composable.errorCount
import com.razz.eva.uow.composable.shouldRejectWith
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.io.OutputStream

// A guarded unit of work never runs as a child: the family implements NotComposableUow.
class GuardedCompileRejectionSpec : FunSpec({

    fun compile(unitMarker: String, parentBody: String) = KotlinCompilation().apply {
        sources = listOf(
            SourceFile.kotlin(
                "Probe.kt",
                """
                package probe

                import com.razz.eva.domain.TestModel
                import com.razz.eva.uow.BaseUnitOfWork.Configuration
                import com.razz.eva.uow.ExecutionContext
                import com.razz.eva.uow.TestPrincipal
                import com.razz.eva.uow.UowParams
                import com.razz.eva.uow.composable.ComposableUow
                import com.razz.eva.uow.composable.UnitOfWork
                import com.razz.eva.uow.guarded.Action
                import com.razz.eva.uow.guarded.GuardedUnitOfWork
                import com.razz.eva.uow.guarded.Guarding
                import com.razz.eva.uow.guarded.Proof
                import com.razz.eva.uow.guarded.Proven

                object Write : Action { override val name = "probe.write" }

                class Allowed(override val target: TestModel) : Proof<TestModel>() {
                    override val principal = TestPrincipal
                    override val action = Write
                    override suspend fun checkAgain(reloaded: TestModel) = Unit
                }

                class Params(val model: Proven<Allowed, TestModel>) : UowParams<Params>

                object ParentParams : UowParams<ParentParams>

                class GuardedUow(executionContext: ExecutionContext) :
                    GuardedUnitOfWork<TestPrincipal, Allowed, Params, TestModel>(
                        executionContext, Configuration.default(), Guarding(TestModel::class), setOf(),
                    )$unitMarker {
                    override suspend fun tryPerform(principal: TestPrincipal, params: Params) =
                        changes { notChanged(params.model.model()) }
                }

                class ParentUow(executionContext: ExecutionContext) :
                    UnitOfWork<TestPrincipal, ParentParams, String>(executionContext) {
                    override suspend fun tryPerform(principal: TestPrincipal, params: ParentParams) = changes {
                        $parentBody
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

    test("A guarded unit of work compiles") {
        compile("", """"ok"""").exitCode shouldBe KotlinCompilation.ExitCode.OK
    }

    test("A guarded unit of work does not compile as an execute argument") {
        val result = compile("", """execute({ ctx -> GuardedUow(ctx) }, principal) { error("no params") }""")
        result.shouldRejectWith("Return type mismatch: expected 'ComposableUow', actual 'GuardedUow'.")
        result.errorCount() shouldBe 1
    }

    test("A guarded unit of work cannot make itself composable") {
        val result = compile(", ComposableUow", """"ok"""")
        result.shouldRejectWith(
            "Type parameter 'A' of 'interface Composition<A> : Any' has inconsistent values: NotComposableUow, " +
                "ComposableUow.",
        )
        result.errorCount() shouldBe 1
    }
})
