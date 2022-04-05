package com.razz.eva.uow

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelId
import com.razz.eva.persistence.ConnectionMode
import com.razz.eva.repository.TransactionalContext

sealed class ExecutionStep {

    data class TransactionStarted(val mode: ConnectionMode) : ExecutionStep()

    data class TransactionFinished(val mode: ConnectionMode) : ExecutionStep()

    data class ModelAdded<ID : ModelId<out Comparable<*>>, M : Model<ID, *>>(
        val context: TransactionalContext,
        val model: M
    ) : ExecutionStep()

    data class ModelUpdated<ID : ModelId<out Comparable<*>>, M : Model<ID, *>>(
        val context: TransactionalContext,
        val model: M
    ) : ExecutionStep()

    data class ModelsAdded<ID : ModelId<out Comparable<*>>, M : Model<ID, *>>(
        val context: TransactionalContext,
        val models: List<M>
    ) : ExecutionStep()

    data class ModelsUpdated<ID : ModelId<out Comparable<*>>, M : Model<ID, *>>(
        val context: TransactionalContext,
        val model: List<M>
    ) : ExecutionStep()

    data class UowEventAdded(val uowEvent: UowEvent<*>) : ExecutionStep()
}
