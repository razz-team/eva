package com.razz.eva.uow.guarded

import com.razz.eva.domain.Employee
import com.razz.eva.uow.params.kotlinx.UowParams
import kotlinx.serialization.Serializable

/** The params of a guarded fixture that writes one employee, under its proof. */
@Serializable
internal data class EmployeeParams(
    val employee: Proven<TestProof<Employee>, Employee>,
) : UowParams<EmployeeParams> {
    override fun serialization() = serializer()
}
