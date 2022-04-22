package com.razz.eva.domain

import com.razz.eva.domain.EmployeeEvent.DepartmentChanged
import com.razz.eva.domain.EmployeeEvent.EmployeeCreated
import com.razz.eva.domain.EntityState.NewState.Companion.newState
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.*

@Serializable
data class EmployeeId(override val id: @Contextual UUID = UUID.randomUUID()) : ModelId<UUID> {
    override fun toString() = id.toString()
}

@Serializable
data class Name(val first: String, val last: String)

class Employee(
    id: EmployeeId,
    val name: Name,
    val departmentId: DepartmentId,
    val email: String,
    val ration: Ration,
    entityState: EntityState<EmployeeId, EmployeeEvent>
) : Model<EmployeeId, EmployeeEvent>(id, entityState) {

    fun changeDepartment(newDepartment: Department<*>): Employee {
        check(newDepartment.id() != departmentId) { "Same department" }
        check(ration == newDepartment.ration) { "Ration should match" }
        return Employee(
            id(),
            name,
            newDepartment.id(),
            email,
            ration,
            entityState().raiseEvent(DepartmentChanged(id(), departmentId, newDepartment.id()))
        )
    }

    override fun toString(): String {
        return """
        Employee(
        id = ${id()}
        name = $name,
        departmentId = $departmentId,
        email = $email,
        ration = $ration
        )
        """.trimIndent()
    }

    override fun equals(other: Any?): Boolean {
        return when (other) {
            is Employee ->
                id() == other.id() &&
                    name == other.name &&
                    departmentId == other.departmentId &&
                    email == other.email &&
                    ration == other.ration
            else -> false
        }
    }

    override fun hashCode(): Int {
        return Objects.hash(id(), name, departmentId, email, ration)
    }

    companion object {
        fun newEmployee(
            name: Name,
            departmentId: DepartmentId,
            email: String,
            ration: Ration
        ): Employee {
            val empId = EmployeeId(UUID.randomUUID())
            return Employee(
                id = empId,
                name = name,
                departmentId = departmentId,
                email = email,
                ration = ration,
                entityState = newState(EmployeeCreated(empId, name, departmentId, email, ration))
            )
        }
    }
}

sealed class EmployeeEvent(override val modelId: EmployeeId) : ModelEvent<EmployeeId> {

    override val modelName = Employee::class.simpleName!!

    data class EmployeeCreated(
        val employeeId: EmployeeId,
        val name: Name,
        val departmentId: DepartmentId,
        val email: String,
        val ration: Ration
    ) : EmployeeEvent(employeeId), ModelCreatedEvent<EmployeeId> {

        override fun integrationEvent() = buildJsonObject {
            put("employeeId", employeeId.stringValue())
            putJsonObject("name") {
                put("first", name.first)
                put("last", name.last)
            }
            put("departmentId", departmentId.stringValue())
            put("email", email)
            put("ration", ration.name)
        }
    }

    data class DepartmentChanged(
        val employeeId: EmployeeId,
        val oldDepartmentId: DepartmentId,
        val newDepartmentId: DepartmentId
    ) : EmployeeEvent(employeeId), ModelEvent<EmployeeId> {
        init {
            check(oldDepartmentId != newDepartmentId) { "Same department" }
        }
    }
}
