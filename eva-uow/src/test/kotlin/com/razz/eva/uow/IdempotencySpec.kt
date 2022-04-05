package com.razz.eva.uow

import com.razz.eva.IdempotencyKey.Companion.idempotencyKey
import com.razz.eva.domain.Bubaleh
import com.razz.eva.domain.Department
import com.razz.eva.domain.Employee
import com.razz.eva.domain.Name
import com.razz.eva.domain.Ration.BUBALEH
import com.razz.eva.persistence.PersistenceException.UniqueUowEventRecordViolationException
import com.razz.eva.repository.BubalehRepository
import com.razz.eva.repository.DepartmentRepository
import com.razz.eva.repository.EmployeeRepository
import com.razz.eva.repository.JooqEventRepository
import com.razz.eva.repository.ModelRepos
import com.razz.eva.repository.ShakshoukaRepository
import com.razz.eva.repository.hasRepo
import com.razz.eva.tracing.Tracing.notReportingTracer
import com.razz.eva.uow.UowEvent.UowName
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Duration
import java.time.Instant.now

class IdempotencySpec : PersistenceBaseSpec({

    Given("Bootstrapped env with hacked persisting") {
        val queryExecutor = testModule.queryExecutor
        val dslContext = testModule.dslContext
        val employeeRepo = EmployeeRepository(queryExecutor, dslContext)
        val departmentRepo = DepartmentRepository(queryExecutor, dslContext)
        val bubalehRepo = BubalehRepository(queryExecutor, dslContext)
        val shakshoukaRepo = ShakshoukaRepository(queryExecutor, dslContext)
        val repos = ModelRepos(
            Department::class hasRepo departmentRepo,
            Employee::class hasRepo employeeRepo,
            Bubaleh::class hasRepo bubalehRepo
        )
        val eventPersisting = JooqEventRepository(queryExecutor, dslContext, notReportingTracer())
        val persisting = Persisting(testModule.transactionManager, repos, eventPersisting)

        fun factories(clock: Clock) = listOf(
            CreateEmployeeUow::class withFactory { CreateEmployeeUow(clock, departmentRepo) },
            CreateSoloDepartmentUow::class withFactory { CreateSoloDepartmentUow(clock, employeeRepo, departmentRepo) },
            HireEmployeesUow::class withFactory { HireEmployeesUow(clock, departmentRepo) },
            TapUow::class withFactory { TapUow(clock, employeeRepo) },
            PartyHardUow::class withFactory { PartyHardUow(clock, departmentRepo, shakshoukaRepo, bubalehRepo) },
        )
        val uowsInPresent = UnitOfWorkExecutor(factories(Clocks.fixedUTC(now())), persisting, notReportingTracer())
        val uowsInFuture = UnitOfWorkExecutor(
            factories(Clocks.fixedUTC(now() + Duration.ofDays(6))),
            persisting,
            notReportingTracer()
        )
        val idempotencyKey = idempotencyKey("they/them")

        When("Principal performs CreateSoloDepartmentUow in present to create department with the boss") {

            val androidBoys = uowsInPresent.execute(CreateSoloDepartmentUow::class, TestPrincipal) {
                CreateSoloDepartmentUow.Params(
                    bossName = Name("Sergey", "Z"),
                    bossEmail = "sergey.z@razz.team",
                    departmentName = "android boys",
                    ration = BUBALEH,
                    idempotencyKey = idempotencyKey
                )
            }
            val sergey = employeeRepo.findByDepartment(androidBoys.id()).single()

            Then("Employee and department are created") {
                sergey.name shouldBe Name("Sergey", "Z")
                sergey.departmentId shouldBe androidBoys.id()
                androidBoys.boss shouldBe sergey.id()
                androidBoys.headcount shouldBe 1
                androidBoys.name shouldBe "android boys"
                androidBoys.ration shouldBe BUBALEH
                sergey.ration shouldBe androidBoys.ration
            }

            When("Principal performs CreateSoloDepartmentUow in future with the new boss and same idempotency key") {
                val attempt = suspend {
                    uowsInFuture.execute(CreateSoloDepartmentUow::class, TestPrincipal) {
                        CreateSoloDepartmentUow.Params(
                            bossName = Name("Ilia", "Z"),
                            bossEmail = "ilia.z@razz.team",
                            departmentName = "new android boys",
                            ration = BUBALEH,
                            idempotencyKey = idempotencyKey
                        )
                    }
                }

                Then("Error is thrown") {
                    val ex = shouldThrow<UniqueUowEventRecordViolationException> {
                        attempt()
                    }
                    ex.uowName shouldBe UowName("CreateSoloDepartmentUow")
                    ex.idempotencyKey shouldBe idempotencyKey("they/them")

                    And("Department should not be created") {
                        val dep = departmentRepo.findByName("new android boys")
                        dep shouldBe null
                    }
                }
            }
        }

        When("There is enough booze") {
            val androidBoys = departmentRepo.findByName("android boys")!!
            uowsInPresent.execute(TapUow::class, TestPrincipal) {
                TapUow.Params(androidBoys.id())
            }

            And("Android boys are going to party hard now") {
                uowsInPresent.execute(PartyHardUow::class, TestPrincipal) {
                    PartyHardUow.Params(androidBoys.id(), idempotencyKey)
                }

                Then("Bubalehs portions are consumed") {
                    val (consumed, served) = bubalehRepo.findAll().fold((0 to 0)) { (cons, serv), sh ->
                        when (sh) {
                            is Bubaleh.Served -> (cons to serv.inc())
                            is Bubaleh.Consumed -> (cons.inc() to serv)
                        }
                    }
                    served shouldBe 0
                    consumed shouldBe departmentRepo.find(androidBoys.id())!!.headcount
                }
            }

            And("There is more booze") {
                uowsInPresent.execute(TapUow::class, TestPrincipal) {
                    TapUow.Params(androidBoys.id())
                }

                And("Android boys are going to party hard in the future") {
                    val attempt = suspend {
                        uowsInPresent.execute(PartyHardUow::class, TestPrincipal) {
                            PartyHardUow.Params(androidBoys.id(), idempotencyKey)
                        }
                    }

                    Then("Error is thrown") {
                        val ex = shouldThrow<UniqueUowEventRecordViolationException> {
                            attempt()
                        }
                        ex.uowName shouldBe UowName("PartyHardUow")
                        ex.idempotencyKey shouldBe idempotencyKey("they/them")

                        val (consumed, served) = bubalehRepo.findAll().fold((0 to 0)) { (cons, serv), sh ->
                            when (sh) {
                                is Bubaleh.Served -> (cons to serv.inc())
                                is Bubaleh.Consumed -> (cons.inc() to serv)
                            }
                        }
                        served shouldBe departmentRepo.find(androidBoys.id())!!.headcount
                        consumed shouldBe departmentRepo.find(androidBoys.id())!!.headcount
                    }
                }
            }
        }
    }
})
