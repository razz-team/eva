package com.razz.eva.repository

import com.razz.eva.domain.Department.OwnedDepartment
import com.razz.eva.domain.DepartmentEvent.OwnedDepartmentCreated
import com.razz.eva.domain.DepartmentId.Companion.randomDepartmentId
import com.razz.eva.domain.Employee
import com.razz.eva.domain.EmployeeEvent.EmployeeCreated
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.EntityState.NewState.Companion.newState
import com.razz.eva.domain.Name
import com.razz.eva.domain.Ration.BUBALEH
import com.razz.eva.domain.Ration.SHAKSHOUKA
import com.razz.eva.domain.Version
import com.razz.eva.domain.Version.Companion.version
import com.razz.eva.test.repository.RepositorySpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe

class DepartmentsRepositorySpec : RepositorySpec(TestEvaRepositoryHelper, {

    Given("Repository is defined") {
        val repo = DepartmentRepository(executor, dslContext)

        And("New department is defined") {
            val department = OwnedDepartment(
                id = randomDepartmentId(),
                name = "KazahDep",
                headcount = 1,
                ration = BUBALEH,
                boss = EmployeeId(),
                entityState = newState(
                    OwnedDepartmentCreated(
                        departmentId = randomDepartmentId(),
                        name = "KazahDep",
                        headcount = 1,
                        ration = BUBALEH,
                        boss = EmployeeId()
                    )
                )
            )

            When("Principal adds new department") {
                inTransaction { context ->
                    repo.add(context, department)
                }

                Then("Department is stored in DB") {
                    val actualDepartment = repo.find(department.id())!!
                    with(actualDepartment) {
                        id() shouldBe department.id()
                        name shouldBe "KazahDep"
                        headcount shouldBe 1
                        ration shouldBe BUBALEH
                    }
                }
            }
        }

        And("Few more departments") {
            val departments = (0..10).map {
                val depId = randomDepartmentId()
                val bossId = EmployeeId()
                OwnedDepartment(
                    id = depId,
                    name = "KazahDep $it",
                    headcount = 1,
                    ration = if (it % 2 == 0) BUBALEH else SHAKSHOUKA,
                    boss = bossId,
                    entityState = newState(
                        OwnedDepartmentCreated(
                            departmentId = depId,
                            name = "KazahDep $it",
                            headcount = 1,
                            ration = if (it % 2 == 0) BUBALEH else SHAKSHOUKA,
                            boss = bossId
                        )
                    )
                )
            }

            When("Principal adds empty list of departments") {
                val attempt = suspend {
                    inTransaction { context ->
                        repo.add(context, listOf())
                    }
                }

                Then("Exception is thrown") {
                    val ex = shouldThrow<IllegalArgumentException> { attempt() }
                    ex.message shouldBe "No models provided for insert"
                }
            }

            val storedDepartments = mutableListOf<OwnedDepartment>()
            When("Principal adds new departments") {
                inTransaction { context ->
                    repo.add(context, departments)
                }

                departments.forEachIndexed { i, department ->
                    Then("Department $i is stored in DB") {
                        val actualDepartment = repo.find(department.id()) as OwnedDepartment
                        with(actualDepartment) {
                            id() shouldBe department.id()
                            name shouldBe "KazahDep $i"
                            headcount shouldBe 1
                            boss shouldBe department.boss
                            ration shouldBe if (i % 2 == 0) BUBALEH else SHAKSHOUKA
                            version() shouldBe Version.V1
                        }
                        storedDepartments.add(actualDepartment)
                    }
                }
            }

            When("Principal updates empty list of departments") {
                val attempt = suspend {
                    inTransaction { context ->
                        repo.update(context, listOf())
                    }
                }

                Then("Exception is thrown") {
                    val ex = shouldThrow<IllegalArgumentException> { attempt() }
                    ex.message shouldBe "No models provided for update"
                }
            }

            When("Principal updates departments") {
                inTransaction { context ->
                    repo.update(
                        context,
                        storedDepartments.mapIndexed { i, d ->
                            val empId = EmployeeId()
                            d.addEmployee(
                                Employee(
                                    id = empId,
                                    name = Name("Employee", i.toString()),
                                    departmentId = d.id(),
                                    email = "employee$i@${d.name}",
                                    ration = d.ration,
                                    entityState = newState(
                                        EmployeeCreated(
                                            empId,
                                            Name("Employee", i.toString()),
                                            d.id(),
                                            "employee$i@${d.name}",
                                            d.ration
                                        )
                                    )
                                )
                            )
                        }
                    )
                }

                storedDepartments.forEachIndexed { i, department ->
                    Then("Department $i is updated in DB") {
                        val actualDepartment = repo.find(department.id())!!
                        with(actualDepartment) {
                            id() shouldBe department.id()
                            name shouldBe "KazahDep $i"
                            headcount shouldBe 2
                            boss shouldBe department.boss
                            ration shouldBe if (i % 2 == 0) BUBALEH else SHAKSHOUKA
                            version() shouldBe version(2)
                        }
                    }
                }
            }
        }
    }
})
