@file:OptIn(ExperimentalCompilerApi::class)

package com.razz.eva.uow.composable

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.OutputStream

// Pins the feature's actual guarantee in CI: a proving block whose last expression is an unregistered
// model must not compile. Without this, the guarantee could be refactored away with every test green.
class ProvingCompileRejectionSpec : FunSpec({

    fun probe(blockTail: String) = SourceFile.kotlin(
        "Probe.kt",
        """
        package probe

        import com.razz.eva.domain.TestModel.CreatedTestModel
        import com.razz.eva.domain.TestModel.Factory.createdTestModel
        import com.razz.eva.uow.Changes
        import com.razz.eva.uow.ExecutionContext
        import com.razz.eva.uow.TestPrincipal
        import com.razz.eva.uow.UowParams
        import com.razz.eva.uow.composable.ProvingUnitOfWork

        object Params : UowParams<Params>

        class ProbeUow(
            executionContext: ExecutionContext,
        ) : ProvingUnitOfWork<TestPrincipal, Params, CreatedTestModel>(executionContext) {

            override suspend fun tryPerform(
                principal: TestPrincipal,
                params: Params,
            ): Changes<CreatedTestModel> = changes {
                $blockTail
            }
        }
        """.trimIndent(),
    )

    fun compile(blockTail: String) = KotlinCompilation().apply {
        sources = listOf(probe(blockTail))
        inheritClassPath = true
        verbose = false
        messageOutputStream = OutputStream.nullOutputStream()
    }.compile()

    test("A block ending on an unregistered model does not compile") {
        val result = compile("createdTestModel(\"MLG\", 420)")
        result.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
        result.messages shouldContain "Accounted"
    }

    test("The same block ending on a registration compiles") {
        val result = compile("add(createdTestModel(\"MLG\", 420))")
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
    }

    test("noModelResult refuses a bare model at compile time") {
        val result = compile("noModelResult(createdTestModel(\"MLG\", 420))")
        result.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
        result.messages shouldContain "must be registered"
    }

    test("An effect block ending on a bare mutation does not compile") {
        val probe = SourceFile.kotlin(
            "EffectProbe.kt",
            """
            package probe

            import com.razz.eva.domain.TestModel.Factory.existingCreatedTestModel
            import com.razz.eva.domain.TestModelId.Companion.randomTestModelId
            import com.razz.eva.domain.Version.Companion.V1
            import com.razz.eva.uow.Changes
            import com.razz.eva.uow.ExecutionContext
            import com.razz.eva.uow.TestPrincipal
            import com.razz.eva.uow.UowParams
            import com.razz.eva.uow.proving.unit.UnitOfWork

            object Params : UowParams<Params>

            class EffectProbeUow(
                executionContext: ExecutionContext,
            ) : UnitOfWork<TestPrincipal, Params>(executionContext) {

                private val model = existingCreatedTestModel(randomTestModelId(), "probe", 1, V1)

                override suspend fun tryPerform(
                    principal: TestPrincipal,
                    params: Params,
                ): Changes<Unit> = changes {
                    notChanged(model)
                    model.activate()
                }
            }
            """.trimIndent(),
        )
        val result = KotlinCompilation().apply {
            sources = listOf(probe)
            inheritClassPath = true
            verbose = false
            messageOutputStream = OutputStream.nullOutputStream()
        }.compile()
        result.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
        result.messages shouldContain "Accounted"
    }

    test("stubChanges is opt-in only: a tryPerform returning it does not compile without the marker") {
        val probe = SourceFile.kotlin(
            "StubProbe.kt",
            """
            package probe

            import com.razz.eva.domain.TestModel.CreatedTestModel
            import com.razz.eva.domain.TestModel.Factory.createdTestModel
            import com.razz.eva.uow.Changes
            import com.razz.eva.uow.ExecutionContext
            import com.razz.eva.uow.TestPrincipal
            import com.razz.eva.uow.UowParams
            import com.razz.eva.uow.composable.ProvingUnitOfWork
            import com.razz.eva.uow.stubChanges

            object Params : UowParams<Params>

            class StubbingUow(
                executionContext: ExecutionContext,
            ) : ProvingUnitOfWork<TestPrincipal, Params, CreatedTestModel>(executionContext) {

                override suspend fun tryPerform(
                    principal: TestPrincipal,
                    params: Params,
                ): Changes<CreatedTestModel> = stubChanges(createdTestModel("MLG", 420))
            }
            """.trimIndent(),
        )
        val result = KotlinCompilation().apply {
            sources = listOf(probe)
            inheritClassPath = true
            verbose = false
            messageOutputStream = OutputStream.nullOutputStream()
        }.compile()
        result.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
        result.messages shouldContain "test doubles"
    }
})
