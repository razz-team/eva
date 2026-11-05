package com.razz.eva.uow.guarded

import com.razz.eva.domain.Department
import com.razz.eva.uow.Changes
import com.razz.eva.uow.ChangesAccumulator
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.composable.UnitOfWork

/** A composable child that renames a department on changes it builds by hand, not over the context it is given. */
internal class HandBuiltChildUow(
    executionContext: ExecutionContext,
    private val department: Department<*>,
) : UnitOfWork<TestPrincipal, ChildParams, String>(executionContext) {

    override suspend fun tryPerform(principal: TestPrincipal, params: ChildParams): Changes<String> =
        ChangesAccumulator().withUpdatedModel(department.rename("Renamed by hand")).withResult("hand built")
}
