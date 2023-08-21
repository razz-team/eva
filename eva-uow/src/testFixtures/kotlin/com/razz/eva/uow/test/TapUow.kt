package com.razz.eva.uow.test

import com.razz.eva.domain.Bubaleh
import com.razz.eva.domain.BubalehBottleVol.OUGH_FIVE
import com.razz.eva.domain.BubalehBottleVol.THIRTY_THREE
import com.razz.eva.domain.BubalehEvent.BubalehCreated
import com.razz.eva.domain.BubalehId
import com.razz.eva.domain.BubalehTaste.SWEEEET
import com.razz.eva.domain.BubalehTaste.VERY_SWEET
import com.razz.eva.domain.DepartmentId
import com.razz.eva.domain.EntityState.NewState.Companion.newState
import com.razz.eva.domain.Ration
import com.razz.eva.repository.EmployeeRepository
import com.razz.eva.uow.UnitOfWork
import kotlinx.serialization.Serializable
import java.time.Clock
import java.time.Instant.now
import java.util.*
import java.util.UUID.randomUUID

class TapUow(
    clock: Clock,
    private val employeeRepo: EmployeeRepository
) : UnitOfWork<TestPrincipal, TapUow.Params, List<Bubaleh>>(clock) {

    private val random = Random()

    @Serializable
    data class Params(val departmentId: DepartmentId) : UowParams<Params> {
        override fun serialization() = serializer()
    }

    override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
        val tapped = mutableListOf<Bubaleh>()
        val emps = employeeRepo.findByDepartment(params.departmentId)
        emps.forEach {
            check(it.ration == Ration.BUBALEH) { "Wrong ration" }
            val id = BubalehId(randomUUID())
            val taste = if (random.nextBoolean()) { SWEEEET } else { VERY_SWEET }
            val vol = if (random.nextBoolean()) { THIRTY_THREE } else { OUGH_FIVE }
            tapped += add(
                Bubaleh.Served(
                    id, it.id(), taste, now(), vol,
                    newState(
                        BubalehCreated(id, it.id(), taste, now(), vol)
                    )
                )
            )
        }
        tapped
    }
}
