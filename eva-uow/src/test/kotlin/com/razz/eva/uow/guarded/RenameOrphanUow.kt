package com.razz.eva.uow.guarded

import com.razz.eva.domain.Department
import com.razz.eva.domain.Department.OrphanedDepartment
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.params.kotlinx.UowParams
import kotlinx.serialization.Serializable

/** Renames a department its narrowing proof allows: the block reads it as an [OrphanedDepartment]. */
internal class RenameOrphanUow(
    executionContext: ExecutionContext,
) : GuardedUnitOfWork<TestPrincipal, OrphanProof, RenameOrphanUow.Params, OrphanedDepartment>(
    executionContext,
    Configuration.default(),
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
}
