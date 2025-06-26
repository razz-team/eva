package com.razz.eva.uow

import com.razz.eva.domain.Department.OwnedDepartment
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelId
import com.razz.eva.domain.Ration.BUBALEH
import com.razz.eva.domain.Ration.SHAKSHOUKA
import com.razz.eva.repository.DepartmentRepository
import com.razz.eva.test.uow.UowBehaviorSpec
import com.razz.eva.uow.CreateDepartmentUow.Params
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.mockk
import java.util.UUID.randomUUID

class UnitOfWorkSpec : UowBehaviorSpec({

    Given("A simple UnitOfWork with repository") {
        val departmentRepo = mockk<DepartmentRepository>()
        val uow = CreateDepartmentUow(clock, departmentRepo)
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

                And("Changes contain Change.Add<Departent>") {
                    changes.toPersist should {
                        it.size shouldBe 1
                        it.filterIsInstance<ModelChange>().single().persist(object : ModelPersisting {
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
