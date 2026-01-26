package com.razz.eva.domain

import java.time.LocalDate

data class RationAllocation(
    val employeeId: EmployeeId,
    val ration: Ration,
    val effectiveDate: LocalDate,
    val quantity: Int,
) : CreatableEntity() {

    init {
        require(quantity > 0) { "Quantity must be positive" }
    }

    companion object {
        fun allocation(
            employeeId: EmployeeId,
            ration: Ration,
            effectiveDate: LocalDate,
            quantity: Int,
        ): RationAllocation = RationAllocation(employeeId, ration, effectiveDate, quantity)

        fun todayAllocation(employeeId: EmployeeId, ration: Ration, quantity: Int): RationAllocation =
            RationAllocation(employeeId, ration, LocalDate.now(), quantity)
    }
}
