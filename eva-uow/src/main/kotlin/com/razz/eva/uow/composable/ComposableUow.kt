package com.razz.eva.uow.composable

/**
 * A unit of work whose change block seeds from [com.razz.eva.uow.ExecutionContext.inheritedChanges],
 * so it can run as a child under [ChangesDsl.execute]: the parent's accumulated changes flow in, the
 * merged set flows back out. The merge is additive, so the parent's changes always survive even under
 * a child constructed with the wrong `ExecutionContext`; the same model changed on diverged event
 * streams fails loudly. Construct the child with the `ExecutionContext` handed to the factory.
 *
 * Sealed because only the bases in this package implement the seeding side of the contract:
 * [UnitOfWork], [ProvingUnitOfWork] and [ProvingEffectUnitOfWork].
 */
sealed interface ComposableUow
