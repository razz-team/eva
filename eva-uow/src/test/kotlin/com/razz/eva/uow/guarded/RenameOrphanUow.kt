package com.razz.eva.uow.guarded

import com.razz.eva.domain.Department
import com.razz.eva.domain.Department.OrphanedDepartment
import com.razz.eva.persistence.PersistenceException
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.params.kotlinx.UowParams
import kotlinx.serialization.Serializable

/**
 * Renames a department its narrowing proof allows: the block reads it as an [OrphanedDepartment]. With [answering]
 * onFailure returns the department the failed attempt read.
 */
internal class RenameOrphanUow(
    executionContext: ExecutionContext,
    configuration: Configuration,
    private val answering: Boolean,
) : GuardedUnitOfWork<TestPrincipal, OrphanProof, RenameOrphanUow.Params, OrphanedDepartment>(
    executionContext,
    configuration,
    Guarding(Department::class),
    setOf(),
) {

    @Serializable
    data class Params(
        val department: Proven<OrphanProof, OrphanedDepartment>,
        val name: String,
    ) : UowParams<Params> {
        override fun serialization() = serializer()
    }

    override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
        val department: OrphanedDepartment = params.department.model()
        update(department) { rename(params.name) }
    }

    override suspend fun onFailure(
        attempt: FailedAttempt,
        params: Params,
        ex: PersistenceException,
    ): OrphanedDepartment = if (answering) attempt[params.department] else throw ex
}
