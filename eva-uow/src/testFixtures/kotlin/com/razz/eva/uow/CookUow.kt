package com.razz.eva.uow

import com.razz.eva.domain.DepartmentId
import com.razz.eva.domain.EggsCount
import com.razz.eva.domain.EntityState.NewState.Companion.newState
import com.razz.eva.domain.Ration
import com.razz.eva.domain.Shakshouka
import com.razz.eva.domain.ShakshoukaEvent.ShakshoukaCreated
import com.razz.eva.domain.ShakshoukaId
import com.razz.eva.repository.EmployeeRepository
import com.razz.eva.uow.basic.UnitOfWork
import kotlinx.serialization.Serializable
import java.time.Clock
import java.util.*
import java.util.UUID.randomUUID

class CookUow(
    clock: Clock,
    private val employeeRepo: EmployeeRepository,
) : UnitOfWork<TestPrincipal, CookUow.Params, List<Shakshouka>>(clock) {

    private val random = Random()

    @Serializable
    data class Params(val departmentId: DepartmentId) : UowParams<Params> {
        override fun serialization() = serializer()
    }

    override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
        val cooked = mutableListOf<Shakshouka>()
        val emps = employeeRepo.findByDepartment(params.departmentId)
        emps.forEach {
            check(it.ration == Ration.SHAKSHOUKA) { "Wrong ration" }
            val id = ShakshoukaId(randomUUID())
            val eggs = if (random.nextBoolean()) { EggsCount.FIVE } else { EggsCount.FOUR }
            val withPita = random.nextBoolean()
            cooked += add(
                Shakshouka.Served(
                    id, it.id(), eggs, withPita,
                    newState(
                        ShakshoukaCreated(id, it.id(), eggs, withPita)
                    )
                )
            )
        }
        cooked
    }
}
