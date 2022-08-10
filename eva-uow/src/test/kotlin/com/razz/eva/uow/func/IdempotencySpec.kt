package com.razz.eva.uow.func

import com.razz.eva.IdempotencyKey.Companion.idempotencyKey
import com.razz.eva.domain.Department.OwnedDepartment
import com.razz.eva.domain.Name
import com.razz.eva.domain.Ration.BUBALEH
import com.razz.eva.persistence.PersistenceException.UniqueUowEventRecordViolationException
import com.razz.eva.uow.CreateEmployeeUow
import com.razz.eva.uow.CreateSoloDepartmentUow
import com.razz.eva.uow.TestPrincipal
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe

class IdempotencySpec : PersistenceBaseSpec({

    Given("Idempotency key") {
        val idempotencyKey = idempotencyKey("new android department")

        And("Params to create models with idempotency key") {
            val params = CreateSoloDepartmentUow.Params(
                bossName = Name("Sergey", "Zarochentsev"),
                bossEmail = "sergey.z@razz.team",
                departmentName = "android boys",
                ration = BUBALEH,
                idempotencyKey = idempotencyKey
            )

            lateinit var androidBoys: OwnedDepartment
            When("Principal runs uow") {
                androidBoys = module.uowx.execute(CreateSoloDepartmentUow::class, TestPrincipal) {
                    params
                }
                val sergey = module.employeeRepo.findByDepartment(androidBoys.id()).single()

                Then("Models are created") {
                    sergey.name shouldBe Name("Sergey", "Zarochentsev")
                    sergey.departmentId shouldBe androidBoys.id()
                    androidBoys.boss shouldBe sergey.id()
                    androidBoys.headcount shouldBe 1
                    androidBoys.name shouldBe "android boys"
                    androidBoys.ration shouldBe BUBALEH
                    sergey.ration shouldBe androidBoys.ration
                }
            }

            When("Principal runs same uow in future with same idempotency key") {
                val attempt = suspend {
                    module.uowxInFuture.execute(CreateSoloDepartmentUow::class, TestPrincipal) {
                        CreateSoloDepartmentUow.Params(
                            bossName = Name("Ilia", "Voitcekhovskii"),
                            bossEmail = "ilia.v@razz.team",
                            departmentName = "new android boys",
                            ration = BUBALEH,
                            idempotencyKey = idempotencyKey
                        )
                    }
                }

                Then("Exception is thrown") {
                    val ex = shouldThrow<UniqueUowEventRecordViolationException> {
                        attempt()
                    }
                    ex.uowName shouldBe "CreateSoloDepartmentUow"
                    ex.idempotencyKey shouldBe idempotencyKey
                }

                And("New department should not be created") {
                    val dep = module.departmentRepo.findByName("new android boys")
                    dep shouldBe null
                }
            }

            When("Principal runs another uow in future with same idempotency key") {
                val newAndroidEmployee = module.uowxInFuture.execute(CreateEmployeeUow::class, TestPrincipal) {
                    CreateEmployeeUow.Params(
                        departmentId = androidBoys.id(),
                        name = Name("Ilia", "Voitcekhovskii"),
                        email = "ilia.v@razz.team",
                        ration = BUBALEH,
                        idempotencyKey = idempotencyKey
                    )
                }

                Then("New model is created") {
                    val persistedEmployee = checkNotNull(module.employeeRepo.find(newAndroidEmployee.id()))
                    persistedEmployee.name shouldBe Name("Ilia", "Voitcekhovskii")
                    persistedEmployee.email shouldBe "ilia.v@razz.team"
                    persistedEmployee.ration shouldBe BUBALEH
                }
            }
        }
    }
})
