package com.razz.eva.uow.composable

/**
 * A unit of work whose change block seeds from [com.razz.eva.uow.ExecutionContext.inheritedChanges],
 * so it can run as a child under [ChangesDsl.execute]: the parent's accumulated changes flow in, the
 * merged set flows back out. A UoW without this contract must not be composed, because [ChangesDsl.execute]
 * replaces the parent's accumulator with whatever the child returns, and a child that started from an
 * empty accumulator would silently drop everything the parent had registered.
 *
 * Sealed because only the bases in this package uphold that contract: [UnitOfWork] and
 * [RegisteringUnitOfWork].
 */
sealed interface Composable
