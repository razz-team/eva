package com.razz.eva.uow.composable

/**
 * Marks a unit of work that can run as a child under [ChangesDsl.execute]: the parent's accumulated changes
 * flow in through [com.razz.eva.uow.ExecutionContext.inheritedChanges], and the merged set flows back out. The
 * child must start from the parent's changes: construct it with the `ExecutionContext` handed to the factory.
 * [UnitOfWork] implements it. A family on [com.razz.eva.uow.WrappingUnitOfWork] opts in by implementing it too.
 * A child's `Configuration` and `onFailure` are not used under a parent: the parent's run decides retries and
 * failure. The exception is the child's [com.razz.eva.uow.PersistOrder]: a custom one carries up, so the parent
 * writes the rows of the whole tree in it, and a composition whose units carry two different custom orders is
 * refused before anything is written.
 *
 * Any class may implement this marker, and that opens nothing a parent does not check. Only eva's change DSLs
 * build a [com.razz.eva.uow.Changes] that carries changes, so a unit of work outside them returns `noChanges`,
 * which merges as nothing, a test stub, or changes another unit of work built. A block that carries changes
 * records the changes it started from, and a block of a [com.razz.eva.uow.WrappingUnitOfWork] records the unit
 * that built it. `execute` refuses the changes of a plain [com.razz.eva.uow.UnitOfWork], of a block another unit
 * built, or of a child built with another context.
 *
 * A unit of work can implement it on its own, whatever its family does. A family that must never run as a child
 * implements [NotComposableUow]: then a unit of it that adds this marker does not compile.
 */
interface ComposableUow : Composition<ComposableUow>
