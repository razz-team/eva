package com.razz.eva.uow

import com.razz.eva.domain.DepartmentId
import com.razz.eva.domain.Tag
import com.razz.eva.uow.composable.UnitOfWork
import kotlinx.serialization.Serializable

class ManageTagsUow(
    executionContext: ExecutionContext,
) : UnitOfWork<TestPrincipal, ManageTagsUow.Params, Unit>(executionContext) {

    @Serializable
    data class Params(
        val departmentId: DepartmentId,
        val tagsToAdd: List<Pair<String, String>>,
        val tagNamesToDelete: List<String>,
    ) : UowParams<Params> {
        override fun serialization() = serializer()
    }

    override suspend fun tryPerform(principal: TestPrincipal, params: Params) = changes {
        params.tagsToAdd.forEach { (name, value) ->
            add(Tag.tag(params.departmentId.id, name, value))
        }
        params.tagNamesToDelete.forEach { tagName ->
            delete(Tag.Key(params.departmentId.id, tagName))
        }
    }
}
