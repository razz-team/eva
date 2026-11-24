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

// A narrowing proof types its unit's reads; a proof that does not narrow cannot stand in for one.
class NarrowingCompileSpec : FunSpec({

    fun compile(body: String) = KotlinCompilation().apply {
        sources = listOf(
            SourceFile.kotlin(
                "Probe.kt",
                """
                package probe

                import com.razz.eva.domain.Department
                import com.razz.eva.domain.Department.OrphanedDepartment
                import com.razz.eva.persistence.PersistenceException
                import com.razz.eva.uow.BaseUnitOfWork.Configuration
                import com.razz.eva.uow.ExecutionContext
                import com.razz.eva.uow.InstantiationContext
                import com.razz.eva.uow.TestPrincipal
                import com.razz.eva.uow.UowParams
                import com.razz.eva.uow.guarded.Action
                import com.razz.eva.uow.guarded.FailedAttempt
                import com.razz.eva.uow.guarded.GuardedUnitOfWork
                import com.razz.eva.uow.guarded.Guarding
                import com.razz.eva.uow.guarded.Proof
                import com.razz.eva.uow.guarded.Proven
                import com.razz.eva.uow.guarded.Proven.Companion.proven

                object Write : Action { override val name = "probe.write" }

                class Narrow(override val target: OrphanedDepartment) : Proof<Department<*>, OrphanedDepartment>() {
                    override val principal = TestPrincipal
                    override val action = Write
                    override suspend fun checkAgain(reloaded: Department<*>) = when (reloaded) {
                        is OrphanedDepartment -> reloaded
                        is Department.OwnedDepartment -> error("has a boss")
                    }
                }

                class Wide(override val target: Department<*>) : Proof<Department<*>, Department<*>>() {
                    override val principal = TestPrincipal
                    override val action = Write
                    override suspend fun checkAgain(reloaded: Department<*>) = reloaded
                }

                $body
                """.trimIndent(),
            ),
        )
        inheritClassPath = true
        jvmTarget = "21"
        languageVersion = "2.2"
        kotlincArguments = listOf("-Xcontext-parameters")
        verbose = false
        messageOutputStream = OutputStream.nullOutputStream()
    }.compile()

    test("A unit of work over a narrowing proof reads and keeps the narrow type") {
        val result = compile(
            """
            class Params(val department: Proven<Narrow, OrphanedDepartment>) : UowParams<Params>

            fun InstantiationContext.params(target: OrphanedDepartment, stored: suspend () -> Department<*>) =
                Params(proven(Narrow(target), stored))

            class RenameUow(executionContext: ExecutionContext) :
                GuardedUnitOfWork<TestPrincipal, Narrow, Params, OrphanedDepartment>(
                    executionContext, Configuration.default(), Guarding(Department::class), setOf(),
                ) {
                override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
                    val department: OrphanedDepartment = params.department.model()
                    update(department) { rename("Renamed") }
                }

                override suspend fun onFailure(attempt: FailedAttempt, params: Params, ex: PersistenceException) =
                    attempt[params.department].let { failed: OrphanedDepartment -> failed }
            }
            """,
        )
        result.messages.lines().filter { it.startsWith("e: ") } shouldBe listOf()
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
    }

    test("A proof that does not narrow gives no Proven of the narrow type") {
        val result = compile(
            """
            class Params(val department: Proven<Proof<*, *>, OrphanedDepartment>) : UowParams<Params>

            fun InstantiationContext.params(target: Department<*>) = Params(proven(Wide(target)) { target })
            """,
        )
        result.shouldRejectWith(
            "Argument type mismatch: actual type is 'Proven<Wide, Department<*>>', but " +
                "'Proven<Proof<*, *>, Department.OrphanedDepartment>' was expected.",
        )
        result.errorCount() shouldBe 1
    }
})
