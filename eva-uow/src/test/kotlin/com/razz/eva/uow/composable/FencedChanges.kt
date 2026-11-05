package com.razz.eva.uow.composable

import com.razz.eva.uow.Changes
import com.razz.eva.uow.EntityChange
import com.razz.eva.uow.ModelChange
import com.razz.eva.uow.PersistedLookup

/** What a [FencedUow]'s block returns: sealed, so only the one subclass beside that block builds it. */
internal sealed class FencedChanges<R>(private val changes: Changes<R>) : Changes<R>() {
    override val result: R get() = changes.result
    override val modelChangesToPersist: List<ModelChange> get() = changes.modelChangesToPersist
    override val entityChangesToPersist: List<EntityChange> get() = changes.entityChangesToPersist
    override val resultBuilder: ((PersistedLookup) -> Any?)? get() = changes.resultBuilder
}
