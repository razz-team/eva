package com.razz.eva.uow

import com.razz.eva.domain.CreatableEntity
import com.razz.eva.domain.DeletableEntity
import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelId
import com.razz.eva.events.UowEvent
import com.razz.eva.persistence.ConnectionMode
import com.razz.eva.repository.TransactionalContext

sealed class ExecutionStep {

    data class TransactionStarted(val mode: ConnectionMode) : ExecutionStep()

    data class TransactionFinished(val mode: ConnectionMode) : ExecutionStep()

    data class ModelAdded<ID : ModelId<out Comparable<*>>, M : Model<ID, *>>(
        val context: TransactionalContext,
        val model: M,
    ) : ExecutionStep()

    data class ModelUpdated<ID : ModelId<out Comparable<*>>, M : Model<ID, *>>(
        val context: TransactionalContext,
        val model: M,
    ) : ExecutionStep()

    data class ModelsAdded<ID : ModelId<out Comparable<*>>, M : Model<ID, *>>(
        val context: TransactionalContext,
        val models: List<M>,
    ) : ExecutionStep()

    data class ModelsUpdated<ID : ModelId<out Comparable<*>>, M : Model<ID, *>>(
        val context: TransactionalContext,
        val model: List<M>,
    ) : ExecutionStep()

    data class EntityAdded<E : CreatableEntity>(
        val context: TransactionalContext,
        val entity: E,
    ) : ExecutionStep()

    data class EntitiesAdded<E : CreatableEntity>(
        val context: TransactionalContext,
        val entities: List<E>,
    ) : ExecutionStep()

    data class EntityDeleted<E : DeletableEntity>(
        val context: TransactionalContext,
        val entity: E,
    ) : ExecutionStep()

    data class EntitiesDeleted<E : DeletableEntity>(
        val context: TransactionalContext,
        val entities: List<E>,
    ) : ExecutionStep()

    data class UowEventAdded(val uowEvent: UowEvent) : ExecutionStep()

    data class UowEventPublished(val uowEvent: UowEvent) : ExecutionStep()
}
