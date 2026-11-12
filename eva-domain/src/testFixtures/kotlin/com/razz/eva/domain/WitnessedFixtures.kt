package com.razz.eva.domain

/**
 * A base for a fixtures object whose members call witnessed mutators and factories. The object is itself a
 * witness for every id type, so its members need no `mutating { }`:
 *
 * ```
 * object EmployeeFixtures : WitnessedFixtures() {
 *     fun aMovedEmployee(department: Department) = anEmployee().changeDepartment(department)
 * }
 * ```
 *
 * The witness reaches only the object's own members and the lambdas inside them; a caller of a member needs none.
 */
abstract class WitnessedFixtures : Witness<ModelId<out Comparable<*>>>()
