package com.razz.eva.uow

import com.razz.eva.domain.Department.OwnedDepartment
import com.razz.eva.domain.DepartmentEvent.EmployeeAdded
import com.razz.eva.domain.DepartmentEvent.EmployeeRemoved
import com.razz.eva.domain.DepartmentId.Companion.randomDepartmentId
import com.razz.eva.domain.Employee
import com.razz.eva.domain.EmployeeEvent.DepartmentChanged
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.Name
import com.razz.eva.domain.Ration.BUBALEH
import com.razz.eva.test.domain.persistentStateV1
import com.razz.eva.repository.DepartmentRepository
import com.razz.eva.repository.EmployeeRepository
import com.razz.eva.test.uow.UowBehaviorSpec
import com.razz.eva.uow.verify.verifyInOrder
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.util.UUID.randomUUID

class UnitOfWorkDemoSpec : UowBehaviorSpec({

    val employeeRepo = mockk<EmployeeRepository>()
    val departmentRepo = mockk<DepartmentRepository>()

    Given("New cool department and old lame department") {
        val newDepId = randomDepartmentId()
        val newBossId = EmployeeId(randomUUID())
        val newDep = OwnedDepartment(
            newDepId, "new and cool", newBossId, 1, BUBALEH,
            persistentStateV1(),
        )
        val oldDepId = randomDepartmentId()
        val oldBossId = EmployeeId(randomUUID())
        val oldDep = OwnedDepartment(
            oldDepId, "old and lame", oldBossId, 3, BUBALEH,
            persistentStateV1()
        )

        When("Zoomer and Boomer are doing internal mobility") {
            val zoomerId = EmployeeId(randomUUID())
            val zoomer = Employee(
                id = zoomerId,
                name = Name("Old", "Zoomer"),
                departmentId = oldDepId,
                email = "old.zoomer@lame.dep",
                ration = BUBALEH,
                modelState = persistentStateV1()
            )
            val boomerId = EmployeeId(randomUUID())
            val boomer = Employee(
                id = boomerId,
                name = Name("Old", "Boomer"),
                departmentId = oldDepId,
                email = "old.boomer@lame.dep",
                ration = BUBALEH,
                modelState = persistentStateV1()
            )

            coEvery { departmentRepo.find(newDepId) } coAnswers { newDep }
            coEvery { departmentRepo.find(oldDepId) } coAnswers { oldDep }
            coEvery { employeeRepo.find(zoomerId) } coAnswers { zoomer }
            coEvery { employeeRepo.find(boomerId) } coAnswers { boomer }

            val changes = InternalMobilityUow(executionContext, employeeRepo, departmentRepo)
                .tryPerform(TestPrincipal, InternalMobilityUow.Params(listOf(zoomerId, boomerId), newDepId))

            Then("First zoomer moved then boomer moved then new dep got two emps then old dep lost two emps") {
                changes verifyInOrder {
                    updatesEq(zoomer.changeDepartment(newDep))
                    updatesEq(boomer.changeDepartment(newDep))
                    updatesEq(newDep.addEmployee(zoomer).addEmployee(boomer))
                    updatesEq(oldDep.removeEmployee(boomer).removeEmployee(zoomer))

                    emitsEq(DepartmentChanged(zoomerId, oldDepId, newDepId))
                    emitsEq(DepartmentChanged(boomerId, oldDepId, newDepId))
                    emitsEq(EmployeeAdded(newDepId, zoomerId, 2))
                    emitsEq(EmployeeAdded(newDepId, boomerId, 3))
                    emitsEq(EmployeeRemoved(oldDepId, zoomerId, 2))
                    emitsEq(EmployeeRemoved(oldDepId, boomerId, 1))

                    returnsEq(Unit)
                }
            }

            And("Models should be updated and Unit should be returned") {
                changes verifyInOrder {
                    updates<Employee> {
                        departmentId shouldBe newDepId
                    }
                    updates<Employee> {
                        departmentId shouldBe newDepId
                    }
                    updates<OwnedDepartment> {
                        headcount shouldBe 3
                    }
                    updates<OwnedDepartment> {
                        headcount shouldBe 1
                    }

                    emits<DepartmentChanged> {
                        employeeId shouldBe zoomerId
                        oldDepartmentId shouldBe oldDepId
                        newDepartmentId shouldBe newDepId
                    }
                    emits<DepartmentChanged> {
                        employeeId shouldBe boomerId
                        oldDepartmentId shouldBe oldDepId
                        newDepartmentId shouldBe newDepId
                    }
                    emits<EmployeeAdded> {
                        departmentId shouldBe newDepId
                        employeeId shouldBe zoomerId
                        newHeadcount shouldBe 2
                    }
                    emits<EmployeeAdded> {
                        departmentId shouldBe newDepId
                        employeeId shouldBe boomerId
                        newHeadcount shouldBe 3
                    }
                    emits<EmployeeRemoved> {
                        departmentId shouldBe oldDepId
                        employeeId shouldBe zoomerId
                        newHeadcount shouldBe 2
                    }
                    emits<EmployeeRemoved> {
                        departmentId shouldBe oldDepId
                        employeeId shouldBe boomerId
                        newHeadcount shouldBe 1
                    }
                    returnsEq(Unit)
                }
            }
        }
    }
})
