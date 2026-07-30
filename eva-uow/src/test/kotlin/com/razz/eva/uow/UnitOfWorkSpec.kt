package com.razz.eva.uow

import com.razz.eva.domain.Department.OwnedDepartment
import com.razz.eva.domain.DepartmentId
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelId
import com.razz.eva.domain.ModelState.PersistentState.Companion.persistentState
import com.razz.eva.domain.Ration.BUBALEH
import com.razz.eva.domain.Ration.SHAKSHOUKA
import com.razz.eva.domain.Version.Companion.V1
import com.razz.eva.repository.DepartmentRepository
import com.razz.eva.test.uow.UowBehaviorSpec
import com.razz.eva.uow.GetOrCreateDepartmentUow.Params
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import java.util.UUID.randomUUID

class UnitOfWorkSpec : UowBehaviorSpec({

    Given("A simple UnitOfWork with repository") {
        val departmentRepo = mockk<DepartmentRepository>()
        val uow = GetOrCreateDepartmentUow(executionContext, departmentRepo)
            as UnitOfWork<TestPrincipal, Params, OwnedDepartment>

        And("Department is not found") {
            coEvery { departmentRepo.findByBoss(any()) } answers { null }

            When("Principal executes unit of work") {
                val empId = EmployeeId(randomUUID())
                val changes = uow.tryPerform(TestPrincipal, Params(empId, "Skunk works", SHAKSHOUKA))

                Then("Changes with Department as a result are returned") {
                    val dep = changes.result
                    dep shouldNotBe null
                    dep.boss shouldBe empId
                    dep.headcount shouldBe 1
                    dep.name shouldBe "Skunk works"
                    dep.ration shouldBe SHAKSHOUKA
                }

                And("Changes contain ModelChange.AddModel<Departent>") {
                    changes.modelChangesToPersist should {
                        it.size shouldBe 1
                        it.single().persist(object : ModelPersisting {
                            override fun <ID : ModelId<out Comparable<*>>, M : Model<ID, *>> add(model: M) {
                                val dep = model as OwnedDepartment
                                dep.boss shouldBe empId
                                dep.headcount shouldBe 1
                                dep.name shouldBe "Skunk works"
                                dep.ration shouldBe SHAKSHOUKA
                            }

                            override fun <ID : ModelId<out Comparable<*>>, M : Model<ID, *>> update(model: M) {
                                TODO("Not used")
                            }
                        })
                    }
                }
            }
        }

        And("Department is found") {
            val empId = EmployeeId(randomUUID())
            val existing = OwnedDepartment(
                id = DepartmentId(randomUUID()),
                name = "Skunk works",
                boss = empId,
                headcount = 1,
                ration = SHAKSHOUKA,
                modelState = persistentState(V1, null),
            )
            coEvery { departmentRepo.findByBoss(empId) } answers { existing }

            When("Principal executes unit of work") {
                val changes = uow.tryPerform(TestPrincipal, Params(empId, "Skunk works", SHAKSHOUKA))

                Then("The existing Department is returned as-is") {
                    changes.result shouldBe existing
                }

                And("Changes abstain, so nothing is persisted and no uow_event is written") {
                    changes.shouldBeInstanceOf<Abstained<OwnedDepartment>>()
                    changes.modelChangesToPersist shouldBe emptyList()
                    changes.entityChangesToPersist shouldBe emptyList()
                }
            }
        }

        And("Department repo throws exception") {
            coEvery { departmentRepo.findByBoss(any()) } answers { throw IllegalStateException() }

            When("Principal executes UnitOfWork") {
                val uowRun = suspend {
                    uow.tryPerform(TestPrincipal, Params(EmployeeId(randomUUID()), "Skunk works", BUBALEH))
                }

                Then("Exception is thrown") {
                    shouldThrow<IllegalStateException> { uowRun() }
                }
            }
        }
    }
})
