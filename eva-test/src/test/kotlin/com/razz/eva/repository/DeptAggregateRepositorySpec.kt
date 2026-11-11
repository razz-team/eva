package com.razz.eva.repository

import com.razz.eva.domain.DepartmentId.Companion.randomDepartmentId
import com.razz.eva.domain.DeptAggregate.Companion.newDeptAggregate
import com.razz.eva.domain.Employee.Companion.newEmployee
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.Name
import com.razz.eva.domain.Ration.BUBALEH
import com.razz.eva.domain.Version.Companion.V1
import com.razz.eva.domain.Version.Companion.version
import com.razz.eva.domain.addEmployee
import com.razz.eva.test.repository.RepositorySpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class DeptAggregateRepositorySpec : RepositorySpec(TestEvaRepositoryHelper, {

    Given("Aggregate repository is defined") {
        val employeeRepo = EmployeeRepository(executor, dslContext)
        val aggRepo = DeptAggregateRepository(
            executor, dslContext, employeeRepo,
        )

        And("New aggregate without children") {
            val bossId = EmployeeId()

            When("Principal adds aggregate root") {
                val dept = newDeptAggregate(
                    name = "Engineering",
                    boss = bossId,
                    ration = BUBALEH,
                )
                val deptId = dept.id()

                val persisted = inTransaction { ctx ->
                    aggRepo.add(ctx, dept)
                }

                Then("Aggregate root is stored") {
                    persisted.id() shouldBe deptId
                    persisted.name shouldBe "Engineering"
                    persisted.version() shouldBe V1
                }

                And("Employees are added separately") {
                    val employee = newEmployee(
                        name = Name("Bob", "Jones"),
                        departmentId = deptId,
                        email = "bob@test.com",
                        ration = BUBALEH,
                    )
                    inTransaction { ctx ->
                        employeeRepo.add(ctx, employee)
                    }

                    Then("Find gathers aggregate with children") {
                        val loaded = aggRepo.find(deptId)!!
                        loaded.employees shouldHaveSize 1
                        loaded.employees[0].name shouldBe Name("Bob", "Jones")
                    }
                }
            }
        }

        And("Aggregate list gathers children for multiple roots") {
            val bossId1 = EmployeeId()
            val bossId2 = EmployeeId()
            val dept1 = newDeptAggregate(
                name = "Marketing",
                boss = bossId1,
                ration = BUBALEH,
            )
            val dept2 = newDeptAggregate(
                name = "Finance",
                boss = bossId2,
                ration = BUBALEH,
            )

            When("Two aggregates are added and queried via list") {
                inTransaction { ctx ->
                    aggRepo.add(ctx, dept1)
                }
                inTransaction { ctx ->
                    aggRepo.add(ctx, dept2)
                }
                // Add an employee to dept1 only
                inTransaction { ctx ->
                    employeeRepo.add(
                        ctx,
                        newEmployee(
                            name = Name("Dave", "Wilson"),
                            departmentId = dept1.id(),
                            email = "dave@test.com",
                            ration = BUBALEH,
                        ),
                    )
                }

                val loaded = aggRepo.list(
                    listOf(dept1.id(), dept2.id()),
                )

                Then("Each aggregate has its own children") {
                    loaded shouldHaveSize 2
                    val d1 = loaded.find { it.id() == dept1.id() }!!
                    val d2 = loaded.find { it.id() == dept2.id() }!!
                    d1.employees shouldHaveSize 1
                    d1.employees[0].name shouldBe Name("Dave", "Wilson")
                    d2.employees.shouldBeEmpty()
                }
            }
        }

        And("Aggregate update preserves model children") {
            val bossId = EmployeeId()
            val dept = newDeptAggregate(
                name = "Operations",
                boss = bossId,
                ration = BUBALEH,
            )

            When("Aggregate is added then renamed") {
                inTransaction { ctx ->
                    aggRepo.add(ctx, dept)
                }
                // Add employee via child repo
                inTransaction { ctx ->
                    employeeRepo.add(
                        ctx,
                        newEmployee(
                            name = Name("Eve", "Davis"),
                            departmentId = dept.id(),
                            email = "eve@test.com",
                            ration = BUBALEH,
                        ),
                    )
                }

                // Reload to get current state
                val loaded = aggRepo.find(dept.id())!!
                loaded.employees shouldHaveSize 1

                // Update root only (rename)
                val updated = inTransaction { ctx ->
                    val modified = loaded.rename("Operations v2")
                    aggRepo.update(ctx, modified)
                }

                Then("Root is updated") {
                    updated.name shouldBe "Operations v2"
                    updated.version() shouldBe version(2)
                }

                Then("Find still gathers model children") {
                    val reloaded = aggRepo.find(dept.id())!!
                    reloaded.employees shouldHaveSize 1
                    reloaded.employees[0].name shouldBe Name("Eve", "Davis")
                }
            }
        }

        And("Add with owned models returns complete aggregate") {
            val bossId = EmployeeId()

            When("Aggregate carrying an employee is added") {
                val dept = newDeptAggregate(
                    name = "WithEmployee",
                    boss = bossId,
                    ration = BUBALEH,
                )
                val employee = newEmployee(
                    name = Name("Alice", "Smith"),
                    departmentId = dept.id(),
                    email = "alice@owned.com",
                    ration = BUBALEH,
                )
                val deptWithEmp = dept.addEmployee(employee)

                val persisted = inTransaction { ctx ->
                    val added = aggRepo.add(ctx, deptWithEmp)
                    employeeRepo.add(ctx, employee)
                    added
                }

                Then("Returned aggregate carries the owned employee") {
                    persisted.id() shouldBe dept.id()
                    persisted.version() shouldBe V1
                    persisted.employees shouldHaveSize 1
                    persisted.employees[0].name shouldBe Name("Alice", "Smith")
                }
            }
        }

        And("Empty ids list returns empty result") {
            When("list is called with empty collection") {
                val result = aggRepo.list(emptyList())

                Then("Result is empty") {
                    result.shouldBeEmpty()
                }
            }
        }

        And("Find returns null for missing id") {
            When("find is called with non-existent id") {
                val result = aggRepo.find(randomDepartmentId())

                Then("Result is null") {
                    result shouldBe null
                }
            }
        }
    }
})
