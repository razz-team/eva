package com.razz.eva.uow.guarded

import com.razz.eva.domain.Department
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.composable.UnitOfWork

/** A plain composable child: renames a department, a model no proof guards. */
internal class RenameDepartmentUow(
    executionContext: ExecutionContext,
    private val department: Department<*>,
) : UnitOfWork<TestPrincipal, ChildParams, Department<*>>(executionContext) {

    override suspend fun tryPerform(principal: TestPrincipal, params: ChildParams) = changes {
        update(department.rename("Renamed by child"))
    }
}
