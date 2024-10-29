package com.razz.eva.domain

import com.razz.eva.domain.DepartmentEvent.BossAdded
import com.razz.eva.domain.DepartmentEvent.BossChanged
import com.razz.eva.domain.DepartmentEvent.EmployeeAdded
import com.razz.eva.domain.DepartmentEvent.EmployeeRemoved
import com.razz.eva.domain.DepartmentEvent.NameChanged
import com.razz.eva.domain.DepartmentEvent.OwnedDepartmentCreated
import com.razz.eva.domain.EntityState.NewState.Companion.newState
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.*
import java.util.UUID.randomUUID

@Serializable
data class DepartmentId(override val id: @Contextual UUID) : ModelId<UUID> {

    companion object {
        fun randomDepartmentId(): DepartmentId = DepartmentId(randomUUID())
    }
}

sealed class Department<SELF : Department<SELF>>(
    id: DepartmentId,
    val name: String,
    val boss: EmployeeId?,
    val headcount: Int,
    val ration: Ration,
    entityState: EntityState<DepartmentId, DepartmentEvent>
) : Model<DepartmentId, DepartmentEvent>(id, entityState) {

    abstract fun copy(
        id: DepartmentId,
        name: String,
        boss: EmployeeId?,
        headcount: Int,
        ration: Ration,
        entityState: EntityState<DepartmentId, DepartmentEvent>
    ): SELF

    class OrphanedDepartment(
        id: DepartmentId,
        name: String,
        headcount: Int,
        ration: Ration,
        entityState: EntityState<DepartmentId, DepartmentEvent>
    ) : Department<OrphanedDepartment>(id, name, null, headcount, ration, entityState) {

        override fun copy(
            id: DepartmentId,
            name: String,
            boss: EmployeeId?,
            headcount: Int,
            ration: Ration,
            entityState: EntityState<DepartmentId, DepartmentEvent>
        ): OrphanedDepartment = OrphanedDepartment(id, name, headcount, ration, entityState)

        fun addBoss(boss: Employee): OwnedDepartment {
            checkRation(boss)
            return OwnedDepartment(
                id(),
                name,
                boss.id(),
                headcount.inc(),
                ration,
                entityState().raiseEvent(BossAdded(id(), boss.id()))
            )
        }

        override fun equals(other: Any?): Boolean {
            return when (other) {
                is OrphanedDepartment ->
                    id() == other.id() &&
                        name == other.name &&
                        boss == other.boss &&
                        headcount == other.headcount &&
                        ration == other.ration
                else -> false
            }
        }

        override fun hashCode(): Int {
            return Objects.hash(id(), name, boss, headcount, ration)
        }
    }

    class OwnedDepartment(
        id: DepartmentId,
        name: String,
        boss: EmployeeId,
        headcount: Int,
        ration: Ration,
        entityState: EntityState<DepartmentId, DepartmentEvent>
    ) : Department<OwnedDepartment>(id, name, boss, headcount, ration, entityState) {

        init {
            check(headcount > 0) { "Мёртвые души" }
        }

        override fun copy(
            id: DepartmentId,
            name: String,
            boss: EmployeeId?,
            headcount: Int,
            ration: Ration,
            entityState: EntityState<DepartmentId, DepartmentEvent>
        ): OwnedDepartment = OwnedDepartment(id, name, boss!!, headcount, ration, entityState)

        fun changeBoss(newBoss: Employee): OwnedDepartment {
            check(newBoss.id() != boss) { "Same boss" }
            checkRation(newBoss)
            return OwnedDepartment(
                id(),
                name,
                newBoss.id(),
                headcount,
                ration,
                entityState().raiseEvent(BossChanged(id(), boss!!, newBoss.id()))
            )
        }
    }

    fun rename(newName: String): SELF {
        check(newName != name) { "Same name" }
        return copy(
            id(), newName, boss, headcount, ration, entityState().raiseEvent(NameChanged(id(), name, newName))
        )
    }

    fun addEmployee(employee: Employee): SELF {
        check(employee.id() != boss) { "Already have a boss" }
        checkRation(employee)
        return copy(
            id(),
            name,
            boss,
            headcount.inc(),
            ration,
            entityState().raiseEvent(EmployeeAdded(id(), employee.id(), headcount.inc()))
        )
    }

    fun removeEmployee(employee: Employee): SELF {
        check(employee.id() != boss) { "Cannot remove boss" }
        checkRation(employee)
        return copy(
            id(),
            name,
            boss,
            headcount.dec(),
            ration,
            entityState().raiseEvent(EmployeeRemoved(id(), employee.id(), headcount.dec()))
        )
    }

    protected fun checkRation(employee: Employee) {
        check(employee.ration == ration) { "Ration should match" }
    }

    override fun equals(other: Any?): Boolean {
        return when (other) {
            is OwnedDepartment ->
                id() == other.id() &&
                    name == other.name &&
                    boss == other.boss &&
                    headcount == other.headcount &&
                    ration == other.ration
            else -> false
        }
    }

    override fun hashCode(): Int {
        return Objects.hash(id(), name, boss, headcount, ration)
    }

    companion object {
        fun newDepartment(
            name: String,
            boss: EmployeeId,
            headcount: Int,
            ration: Ration
        ): OwnedDepartment {
            val depId = DepartmentId(randomUUID())
            return OwnedDepartment(
                id = depId,
                name = name,
                boss = boss,
                headcount = headcount,
                ration = ration,
                entityState = newState(OwnedDepartmentCreated(depId, name, boss, 1, ration))
            )
        }
    }
}

sealed class DepartmentEvent(
    override val modelId: DepartmentId
) : ModelEvent<DepartmentId> {

    override val modelName = Department::class.simpleName!!

    data class OwnedDepartmentCreated(
        val departmentId: DepartmentId,
        val name: String,
        val boss: EmployeeId,
        val headcount: Int,
        val ration: Ration
    ) : DepartmentEvent(departmentId), ModelCreatedEvent<DepartmentId> {

        override fun integrationEvent() = buildJsonObject {
            put("name", name)
            put("boss", boss.stringValue())
            put("headcount", headcount)
            put("ration", ration.name)
        }
    }

    data class OrphanedDepartmentCreated(
        val departmentId: DepartmentId,
        val name: String,
        val headcount: Int,
        val ration: Ration
    ) : DepartmentEvent(departmentId), ModelCreatedEvent<DepartmentId>, ModelWithPrincipalEvent<DepartmentId> {

        override fun integrationEvent() = buildJsonObject {
            put("name", name)
            put("headcount", headcount)
            put("ration", ration.name)
        }
    }

    data class BossChanged(
        val departmentId: DepartmentId,
        val oldBossId: EmployeeId,
        val newBossId: EmployeeId
    ) : DepartmentEvent(departmentId), ModelEvent<DepartmentId> {
        init {
            check(newBossId != oldBossId) { "Same boss" }
        }
    }

    data class BossAdded(
        val departmentId: DepartmentId,
        val bossId: EmployeeId
    ) : DepartmentEvent(departmentId), ModelEvent<DepartmentId>

    data class NameChanged(
        val departmentId: DepartmentId,
        val oldName: String,
        val newName: String
    ) : DepartmentEvent(departmentId), ModelEvent<DepartmentId> {
        init {
            check(newName != oldName) { "Same name" }
        }
    }

    data class EmployeeAdded(
        val departmentId: DepartmentId,
        val employeeId: EmployeeId,
        val newHeadcount: Int
    ) : DepartmentEvent(departmentId), ModelEvent<DepartmentId>

    data class EmployeeRemoved(
        val departmentId: DepartmentId,
        val employeeId: EmployeeId,
        val newHeadcount: Int
    ) : DepartmentEvent(departmentId), ModelEvent<DepartmentId>
}
