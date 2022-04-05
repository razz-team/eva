package com.razz.eva.uow

import com.razz.eva.IdempotencyKey.Companion.idempotencyKey
import com.razz.eva.domain.Bubaleh
import com.razz.eva.domain.Department
import com.razz.eva.domain.DepartmentId
import com.razz.eva.domain.Employee
import com.razz.eva.domain.Name
import com.razz.eva.domain.Ration.BUBALEH
import com.razz.eva.domain.Ration.SHAKSHOUKA
import com.razz.eva.domain.Shakshouka
import com.razz.eva.domain.ShakshoukaId
import com.razz.eva.persistence.ConnectionMode
import com.razz.eva.persistence.PersistenceException
import com.razz.eva.persistence.PersistenceException.ModelRecordConstraintViolationException
import com.razz.eva.persistence.WithCtxConnectionTransactionManager
import com.razz.eva.repository.BubalehRepository
import com.razz.eva.repository.DepartmentRepository
import com.razz.eva.repository.EmployeeRepository
import com.razz.eva.repository.ModelRepos
import com.razz.eva.repository.PreModifyCallback
import com.razz.eva.repository.ShakshoukaRepository
import com.razz.eva.repository.TransactionalContext.Companion.transactionalContext
import com.razz.eva.repository.hasRepo
import com.razz.eva.tracing.Tracing.noopTracer
import com.razz.eva.uow.Clocks.fixedUTC
import com.razz.eva.uow.Clocks.millisUTC
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.time.Instant.now
import java.util.*
import java.util.UUID.randomUUID

class TransactionalSpec : PersistenceBaseSpec({

    val now = millisUTC().instant()
    val clock = fixedUTC(now)

    Given("Bootstrapped env with hacked persisting") {
        val queryExecutor = testModule.queryExecutor
        val dslContext = testModule.dslContext
        val employeeRepo = EmployeeRepository(queryExecutor, dslContext)
        val bubalehRepo = BubalehRepository(queryExecutor, dslContext)
        var preUpdateShakshouka: PreModifyCallback<UUID, ShakshoukaId, Shakshouka> = PreModifyCallback { }
        var preUpdateDepartment: PreModifyCallback<UUID, DepartmentId, Department<*>> = PreModifyCallback { }
        val shakshoukaRepo = ShakshoukaRepository(queryExecutor, dslContext, { preUpdateShakshouka(it) })
        val departmentRepo = DepartmentRepository(queryExecutor, dslContext, { preUpdateDepartment(it) })
        val repos = ModelRepos(
            Department::class hasRepo departmentRepo,
            Employee::class hasRepo employeeRepo,
            Bubaleh::class hasRepo bubalehRepo,
            Shakshouka::class hasRepo shakshoukaRepo
        )
        val eventPersisting = DummyEventRepository()
        val persisting = Persisting(testModule.transactionManager, repos, eventPersisting)
        var afterFailedTransaction: suspend () -> Unit = { }
        val hackedTxnManager = WithCtxConnectionTransactionManager(
            wrapped = testModule.transactionManager,
            afterFailedTransaction = { afterFailedTransaction() }
        )
        val hackedPersisting = Persisting(hackedTxnManager, repos, eventPersisting)

        val factories = listOf(
            CreateDepartmentUow::class withFactory { CreateDepartmentUow(clock, departmentRepo) },
            CreateEmployeeUow::class withFactory { CreateEmployeeUow(clock, departmentRepo) },
            CreateSoloDepartmentUow::class withFactory { CreateSoloDepartmentUow(clock, employeeRepo, departmentRepo) },
            HireEmployeesUow::class withFactory { HireEmployeesUow(clock, departmentRepo) },
            CookUow::class withFactory { CookUow(clock, employeeRepo) },
            PartyHardUow::class withFactory { PartyHardUow(clock, departmentRepo, shakshoukaRepo, bubalehRepo) },
            InternalMobilityUow::class withFactory { InternalMobilityUow(clock, employeeRepo, departmentRepo) }
        )
        val uows = UnitOfWorkExecutor(factories, persisting, noopTracer())
        val hackedUows = UnitOfWorkExecutor(factories, hackedPersisting, noopTracer())

        When("Principal performs CreateSoloDepartmentUow to create department with the boss") {
            val pocontre = uows.execute(CreateSoloDepartmentUow::class, TestPrincipal) {
                CreateSoloDepartmentUow.Params(
                    bossName = Name("Niky", "Dnk"),
                    bossEmail = "niky@razz.team",
                    departmentName = "pocontre",
                    ration = SHAKSHOUKA,
                    idempotencyKey = idempotencyKey(randomUUID())
                )
            }
            val boss = employeeRepo.findByDepartment(pocontre.id()).single()

            Then("Employee and department are created") {
                boss.name shouldBe Name("Niky", "Dnk")
                boss.departmentId shouldBe pocontre.id()
                pocontre.boss shouldBe boss.id()
                pocontre.headcount shouldBe 1
                pocontre.name shouldBe "pocontre"
                pocontre.ration shouldBe SHAKSHOUKA
                boss.ration shouldBe pocontre.ration
            }
        }

        val pocontre = departmentRepo.findByName("pocontre")!!
        val kirilka = uows.execute(HireEmployeesUow::class, TestPrincipal) {
            HireEmployeesUow.Params(pocontre.id(), listOf(Name("K", "🍄")))
        }.single()

        When("Principal performs HireEmployeesUow to add 🍄, 🍋 and Kaplin to pocontre") {
            val pocontre = departmentRepo.findByName("pocontre")!!
            val uowRun = suspend {
                uows.execute(HireEmployeesUow::class, TestPrincipal) {
                    HireEmployeesUow.Params(
                        pocontre.id(),
                        listOf(Name("K", "🍄"), Name("I", "🍋"), Name("A", "Kaplin"))
                    )
                }
            }

            Then("adding all of them fails because 🍄 already hired") {
                val ex = shouldThrow<PersistenceException.UniqueModelRecordViolationException> { uowRun() }
                ex.constraintName shouldBe "employees_name_idx"
            }

            And("🍄 should stay hired while 🍋 and Kaplin should be non-hired") {
                employeeRepo.findByName(Name("K", "🍄")) shouldBe kirilka
                employeeRepo.findByName(Name("I", "🍋")) shouldBe null
                employeeRepo.findByName(Name("A", "Kaplin")) shouldBe null
            }
        }

        When("Principal performs HireEmployeesUow to add Nursultan Äbishuly Nazarbayev") {
            val pocontre = departmentRepo.findByName("pocontre")!!
            val uowRun = suspend {
                uows.execute(HireEmployeesUow::class, TestPrincipal) {
                    HireEmployeesUow.Params(pocontre.id(), listOf(Name("Nursultan Äbishuly Nazarbayev", "01")))
                }
            }

            Then("adding all of them fails because Nursultan's name is too long") {
                val ex = shouldThrow<ModelRecordConstraintViolationException> { uowRun() }
                ex.constraintName shouldBe "employees_first_name_len_check"
            }

            And("🍄 should stay hired while Nursultan should be non-hired") {
                employeeRepo.findByName(Name("K", "🍄")) shouldBe kirilka
                employeeRepo.findByName(Name("Nursultan Äbishuly Nazarbayev", "01")) shouldBe null
            }
        }

        val kislii = uows.execute(HireEmployeesUow::class, TestPrincipal) {
            HireEmployeesUow.Params(pocontre.id(), listOf(Name("I", "🍋")))
        }.single()

        When("There is enough food") {
            uows.execute(CookUow::class, TestPrincipal) {
                CookUow.Params(pocontre.id())
            }
        }

        And("Pocontre are going to party hard but party is raided by transactional police") {

            var updated = 0
            preUpdateShakshouka = PreModifyCallback {
                if (++updated == pocontre.headcount) {
                    preUpdateShakshouka = PreModifyCallback { }
                    testModule.transactionManager.inTransaction(
                        ConnectionMode.REQUIRE_NEW,
                        suspend {
                            shakshoukaRepo.update(
                                transactionalContext(now()),
                                (shakshoukaRepo.find(it.id) as Shakshouka.Served).consume()
                            )
                        }
                    )
                }
            }

            val uowRun = suspend {
                hackedUows.execute(PartyHardUow::class, TestPrincipal) {
                    PartyHardUow.Params(pocontre.id(), idempotencyKey(randomUUID()))
                }
            }

            Then("Shakshoukas portions are rolled back, idk guys probably vomited it") {
                shouldThrow<PersistenceException.StaleRecordException> { uowRun() }
                val (consumed, served) = shakshoukaRepo.findAll().fold((0 to 0)) { (cons, serv), sh ->
                    when (sh) {
                        is Shakshouka.Served -> (cons to serv.inc())
                        is Shakshouka.Consumed -> (cons.inc() to serv)
                    }
                }
                // 1 consumed concurrently in hacked repo
                consumed shouldBe 1
                served shouldBe departmentRepo.find(pocontre.id())!!.headcount - 1
            }
        }

        When("There is Kaplin preferring bubaleh trying to join pocontre") {
            val trading = uows.execute(CreateSoloDepartmentUow::class, TestPrincipal) {
                CreateSoloDepartmentUow.Params(
                    bossName = Name("D", "Vasin"),
                    bossEmail = "vasin@boss.kaplina",
                    departmentName = "Trading",
                    ration = BUBALEH,
                    idempotencyKey = idempotencyKey(randomUUID())
                )
            }
            val kaplin = uows.execute(HireEmployeesUow::class, TestPrincipal) {
                HireEmployeesUow.Params(trading.id(), listOf(Name("A", "Kaplin")))
            }.single()
            val deposits = uows.execute(CreateSoloDepartmentUow::class, TestPrincipal) {
                CreateSoloDepartmentUow.Params(
                    bossName = Name("Marsel", "N"),
                    bossEmail = "zemlya@puhom.revolut",
                    departmentName = "Deposits",
                    ration = SHAKSHOUKA,
                    idempotencyKey = idempotencyKey(randomUUID())
                )
            }
            val depositBoys = uows.execute(HireEmployeesUow::class, TestPrincipal) {
                HireEmployeesUow.Params(
                    deposits.id(),
                    listOf(Name("Toha", "R"), Name("Ilyha", "M"), Name("Georgy", "F"))
                )
            }
            val uowRun = suspend {
                hackedUows.execute(InternalMobilityUow::class, TestPrincipal) {
                    InternalMobilityUow.Params((depositBoys + kaplin).map { it.id() }, pocontre.id())
                }
            }

            Then("Sorry, try on next performance review") {
                val ex = shouldThrow<Exception> { uowRun() }
                ex.message shouldBe "Ration doesn't match"
                employeeRepo.findByDepartment(deposits.id()).map { it.name }.toSet() shouldBe
                    setOf(Name("Marsel", "N"), Name("Toha", "R"), Name("Ilyha", "M"), Name("Georgy", "F"))
                employeeRepo.findByDepartment(trading.id()).map { it.name }.toSet() shouldBe
                    setOf(Name("D", "Vasin"), Name("A", "Kaplin"))
                employeeRepo.findByDepartment(pocontre.id()).map { it.name }.toSet() shouldBe
                    setOf(Name("Niky", "Dnk"), Name("K", "🍄"), Name("I", "🍋"))
            }
        }

        When("There is new cool department ran by SergeyP") {
            val topBoys = uows.execute(CreateSoloDepartmentUow::class, TestPrincipal) {
                CreateSoloDepartmentUow.Params(
                    Name("Sergey", "P"),
                    "sergeyp@topboys.razz",
                    "Top Boys",
                    SHAKSHOUKA,
                    idempotencyKey(randomUUID())
                )
            }

            And("pocontre willing to join it but uow fails on first run") {

                preUpdateDepartment = PreModifyCallback {
                    if (it.id() == pocontre.id()) {
                        preUpdateDepartment = PreModifyCallback { }
                        testModule.transactionManager.inTransaction(
                            ConnectionMode.REQUIRE_NEW,
                            suspend {
                                departmentRepo.update(
                                    transactionalContext(now()),
                                    departmentRepo.find(it.id())!!.rename("renamed dep")
                                )
                            }
                        )
                    }
                }

                var retried = false
                afterFailedTransaction = suspend {
                    retried = true
                    afterFailedTransaction = { }
                    employeeRepo.findByDepartment(topBoys.id()).map { it.name }.toSet() shouldBe
                        setOf(Name("Sergey", "P"))
                    departmentRepo.find(topBoys.id())!!.headcount shouldBe 1
                    employeeRepo.findByDepartment(pocontre.id()).map { it.name }.toSet() shouldBe
                        setOf(Name("Niky", "Dnk"), Name("K", "🍄"), Name("I", "🍋"))
                    departmentRepo.find(pocontre.id())!!.headcount shouldBe 3
                }

                hackedUows.execute(InternalMobilityUow::class, TestPrincipal) {
                    InternalMobilityUow.Params(listOf(kirilka.id(), kislii.id()), topBoys.id())
                }

                Then("UoW should be retried and pocontre should be transferred") {
                    retried shouldBe true
                    employeeRepo.findByDepartment(topBoys.id()).map { it.name }.toSet() shouldBe
                        setOf(Name("Sergey", "P"), Name("K", "🍄"), Name("I", "🍋"))
                    departmentRepo.find(topBoys.id())!!.headcount shouldBe 3
                    employeeRepo.findByDepartment(pocontre.id()).map { it.name }.toSet() shouldBe
                        setOf(Name("Niky", "Dnk"))
                    departmentRepo.find(pocontre.id())!!.headcount shouldBe 1
                }
            }
        }
    }
})
