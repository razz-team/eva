package com.razz.eva.uow

import com.razz.eva.domain.DepartmentId
import com.razz.eva.domain.Tag
import com.razz.eva.uow.composable.UnitOfWork
import kotlinx.serialization.Serializable

class CleanupTagsUow(
    executionContext: ExecutionContext,
) : UnitOfWork<TestPrincipal, CleanupTagsUow.Params, Int>(executionContext) {

    @Serializable
    data class Params(
        val departmentId: DepartmentId,
        val tagNames: List<String>,
    ) : UowParams<Params> {
        override fun serialization() = serializer()
    }

    override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
        params.tagNames.forEach { tagName ->
            delete(Tag.Key(params.departmentId.id, tagName))
        }
        params.tagNames.size
    }
}
