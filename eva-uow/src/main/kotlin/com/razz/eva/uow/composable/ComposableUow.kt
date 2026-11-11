package com.razz.eva.uow.composable

/**
 * A unit of work whose change block seeds from [com.razz.eva.uow.ExecutionContext.inheritedChanges],
 * so it can run as a child under [ChangesDsl.execute]: the parent's accumulated changes flow in, the
 * merged set flows back out. The merge is additive, so the parent's changes always survive even under
 * a child constructed with the wrong `ExecutionContext`; the same model changed on diverged event
 * streams fails loudly. Construct the child with the `ExecutionContext` handed to the factory.
 *
 * Sealed because only [UnitOfWork] and [WrappingUnitOfWork] in this package are built on the seeding
 * [ChangesDsl]. That is deliberate, and it means a third-party base cannot be composable: seeding, merging
 * and the guards that police them move together, so a family outside this package could satisfy the marker
 * without satisfying the contract. A family with a change block of its own extends [WrappingUnitOfWork].
 */
sealed interface ComposableUow
