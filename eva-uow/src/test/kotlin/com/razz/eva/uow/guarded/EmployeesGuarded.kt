package com.razz.eva.uow.guarded

import com.razz.eva.domain.Employee

/** The fixtures' guarded model: employees need a proof, departments do not. */
internal val EmployeesGuarded = Guarding(Employee::class)
