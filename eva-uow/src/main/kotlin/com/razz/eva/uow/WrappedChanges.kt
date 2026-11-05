package com.razz.eva.uow

/**
 * The changes a DSL built, under a type of the framework's own. A unit of work whose block may return only what
 * its own `changes { }` produced declares a subclass that only its `changes` constructs (a sealed class with the
 * one subclass beside it) and narrows `tryPerform` to it, so [BaseUnitOfWork.noChanges] and changes built anywhere
 * else fail to compile. Every field forwards: the executor persists and returns exactly what the DSL built.
 */
abstract class WrappedChanges<R>(private val changes: Changes<R>) : Changes<R>() {
    final override val result: R get() = changes.result
    final override val modelChangesToPersist: List<ModelChange> get() = changes.modelChangesToPersist
    final override val entityChangesToPersist: List<EntityChange> get() = changes.entityChangesToPersist
    final override val resultBuilder: ((PersistedLookup) -> Any?)? get() = changes.resultBuilder
}
