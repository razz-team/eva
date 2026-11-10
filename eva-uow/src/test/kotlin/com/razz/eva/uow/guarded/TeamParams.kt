package com.razz.eva.uow.guarded

import com.razz.eva.domain.Employee
import com.razz.eva.uow.params.kotlinx.UowParams
import kotlinx.serialization.Serializable

/** Several employees, each in a Proven field of its own. */
@Serializable
internal data class TeamParams(
    val team: List<Proven<TestProof<Employee>, Employee>>,
) : UowParams<TeamParams> {
    override fun serialization() = serializer()
}
