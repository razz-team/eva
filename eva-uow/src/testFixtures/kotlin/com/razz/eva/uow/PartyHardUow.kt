package com.razz.eva.uow

import com.razz.eva.IdempotencyKey
import com.razz.eva.domain.DepartmentId
import com.razz.eva.domain.Ration
import com.razz.eva.repository.BubalehRepository
import com.razz.eva.repository.DepartmentRepository
import com.razz.eva.repository.ShakshoukaRepository
import kotlinx.serialization.Serializable
import java.time.Clock

class PartyHardUow(
    clock: Clock,
    private val departmentRepo: DepartmentRepository,
    private val shakshoukaRepo: ShakshoukaRepository,
    private val bubalehRepo: BubalehRepository,
) : UnitOfWork<TestPrincipal, PartyHardUow.Params, Unit>(clock, Configuration(retry = null)) {

    @Serializable
    data class Params(
        val departmentId: DepartmentId,
        override val idempotencyKey: IdempotencyKey
    ) : UowParams<Params> {
        override fun serialization() = serializer()
    }

    override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
        when (val dep = departmentRepo.find(params.departmentId)) {
            null -> throw IllegalStateException("no department")
            else -> when (dep.ration) {
                Ration.SHAKSHOUKA -> shakshoukaRepo.findNCookedPortions(dep.headcount).forEach {
                    update(it.consume())
                }
                Ration.BUBALEH -> bubalehRepo.findNBottles(dep.headcount).forEach {
                    update(it.consume())
                }
            }
        }
    }
}
