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
})
