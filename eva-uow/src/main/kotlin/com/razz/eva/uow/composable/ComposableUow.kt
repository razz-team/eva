package com.razz.eva.uow.composable

/**
 * Marks a unit of work that can run as a child under [ChangesDsl.execute]: the parent's accumulated changes
 * flow in through [com.razz.eva.uow.ExecutionContext.inheritedChanges], and the merged set flows back out. The
 * child must start from the parent's changes: construct it with the `ExecutionContext` handed to the factory.
 * [UnitOfWork] implements it. A family on [com.razz.eva.uow.WrappingUnitOfWork] opts in by implementing it too.
 *
 * Any class may implement this marker, and that opens nothing. Only eva's change DSLs build a
 * [com.razz.eva.uow.Changes] that carries changes, so a unit of work outside them can return only `noChanges`,
 * which merges as nothing, or a test stub. A block that does carry changes also records the changes it started
 * from, and `execute` refuses a child whose block did not start from the parent's: a plain
 * [com.razz.eva.uow.UnitOfWork] that claims this marker, or a child built with another context, fails there.
 */
interface ComposableUow
