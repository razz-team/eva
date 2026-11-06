package com.razz.eva.uow.guarded

import com.razz.eva.domain.Employee
import com.razz.eva.domain.Model
import kotlin.reflect.KClass

/** The fixtures' guarded aggregate: employees take a proof, departments do not. */
internal object EmployeesGuarded : Guarding {
    override fun covers(type: KClass<out Model<*, *>>): Boolean =
        Employee::class.java.isAssignableFrom(type.java) || type.java.isAssignableFrom(Employee::class.java)
}
