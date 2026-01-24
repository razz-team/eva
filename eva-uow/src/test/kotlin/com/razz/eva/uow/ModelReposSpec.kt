package com.razz.eva.uow

import com.razz.eva.domain.Department
import com.razz.eva.domain.Department.OwnedDepartment
import com.razz.eva.domain.DepartmentEvent.OwnedDepartmentCreated
import com.razz.eva.domain.DepartmentId.Companion.randomDepartmentId
import com.razz.eva.domain.EggsCount
import com.razz.eva.domain.Employee
import com.razz.eva.domain.EmployeeEvent
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.ModelState.NewState.Companion.newState
import com.razz.eva.domain.Name
import com.razz.eva.domain.Ration
import com.razz.eva.domain.Shakshouka
import com.razz.eva.domain.ShakshoukaEvent.ShakshoukaCreated
import com.razz.eva.domain.ShakshoukaId
import com.razz.eva.repository.DepartmentRepository
import com.razz.eva.repository.EmployeeRepository
import com.razz.eva.repository.ModelRepos
import com.razz.eva.repository.RepositoryNotFoundException
import com.razz.eva.repository.hasRepo
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import java.util.UUID.randomUUID

class ModelReposSpec : BehaviorSpec({

    Given("ModelRepos is configured only for Department and Employee") {
        val departmentRepo = DepartmentRepository(mockk(), mockk())
        val employeeRepo = EmployeeRepository(mockk(), mockk())

        val modelRepos = ModelRepos(
            Department::class hasRepo departmentRepo,
            Employee::class hasRepo employeeRepo
        )

        And("Owned department model is defined") {
            val depId = randomDepartmentId()
            val boss = EmployeeId(randomUUID())
            val department = OwnedDepartment(
                id = depId,
                name = "Test Department",
                boss = boss,
                headcount = 1,
                ration = Ration.BUBALEH,
                modelState = newState(
                    OwnedDepartmentCreated(
                        departmentId = depId,
                        name = "Test Department",
                        boss = boss,
                        headcount = 1,
                        ration = Ration.BUBALEH
                    )
                )
            )

            When("Principal gets repository for model") {
                val repo = modelRepos.repoFor(department)

                Then("Principal should get a correct repository") {
                    repo shouldBe departmentRepo
                }
            }
        }

        And("Employee is defined") {
            val employeeId = EmployeeId()
            val depId = randomDepartmentId()
            val employee = Employee(
                id = employeeId,
                name = Name("Sergey", "P"),
                departmentId = depId,
                email = "sergey@razz.team",
                ration = Ration.BUBALEH,
                modelState = newState(
                    createdEvent = EmployeeEvent.EmployeeCreated(
                        employeeId = employeeId,
                        name = Name("Sergey", "P"),
                        departmentId = depId,
                        email = "sergey@razz.team",
                        ration = Ration.BUBALEH
                    )
                )
            )

            When("Principal gets repository for model") {
                val repo = modelRepos.repoFor(employee)

                Then("Principal should get a correct repository") {
                    repo shouldBe employeeRepo
                }
            }
        }

        And("Shakshouka models is defined") {
            val shakshouka = Shakshouka.Consumed(
                id = ShakshoukaId(),
                employeeId = EmployeeId(),
                eggsCount = EggsCount.FIVE,
                withPita = true,
                modelState = newState(
                    createdEvent = ShakshoukaCreated(
                        shakshoukaId = ShakshoukaId(),
                        employeeId = EmployeeId(),
                        eggsCount = EggsCount.FIVE,
                        withPita = true
                    )
                )
            )

            When("Principal gets repository for model") {
                val getRepo = suspend {
                    modelRepos.repoFor(shakshouka)
                }

                Then("Principal should get an exception") {
                    shouldThrow<RepositoryNotFoundException> {
                        getRepo()
                    }
                }
            }
        }
    }
})
