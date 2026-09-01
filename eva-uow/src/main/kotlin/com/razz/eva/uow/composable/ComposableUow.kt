package com.razz.eva.uow.composable

/**
 * A unit of work whose change block seeds from [com.razz.eva.uow.ExecutionContext.inheritedChanges],
 * so it can run as a child under [ChangesDsl.execute]: the parent's accumulated changes flow in, the
 * merged set flows back out. That only holds when the child is constructed with the `ExecutionContext`
 * handed to the factory; a factory that substitutes its own context starts the child from an empty
 * accumulator, which is why [ChangesDsl.execute] verifies after the merge that no inherited change was
 * dropped.
 *
 * Sealed because only the bases in this package implement the seeding side of the contract:
 * [UnitOfWork] and [ProvingUnitOfWork].
 */
sealed interface ComposableUow
