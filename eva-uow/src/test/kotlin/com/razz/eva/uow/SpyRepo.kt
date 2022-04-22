package com.razz.eva.uow

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelId
import com.razz.eva.repository.ModelRepository
import com.razz.eva.repository.TransactionalContext
import com.razz.eva.uow.ExecutionStep.ModelAdded
import com.razz.eva.uow.ExecutionStep.ModelUpdated
import com.razz.eva.uow.ExecutionStep.ModelsAdded
import com.razz.eva.uow.ExecutionStep.ModelsUpdated

class SpyRepo(
    private val history: MutableList<ExecutionStep>
) : ModelRepository<Comparable<Any>, ModelId<Comparable<Any>>, Model<ModelId<Comparable<Any>>, *>> {

    override suspend fun find(id: ModelId<Comparable<Any>>): Model<ModelId<Comparable<Any>>, *>? {
        TODO("Not used")
    }

    override suspend fun <ME : Model<ModelId<Comparable<Any>>, *>> add(
        context: TransactionalContext,
        model: ME
    ): ME {
        history.add(ModelAdded(context, model))
        return model
    }

    override suspend fun <ME : Model<ModelId<Comparable<Any>>, *>> add(
        context: TransactionalContext,
        models: List<ME>
    ) {
        history.add(ModelsAdded(context, models))
    }

    override suspend fun <ME : Model<ModelId<Comparable<Any>>, *>> update(
        context: TransactionalContext,
        model: ME
    ): ME {
        history.add(ModelUpdated(context, model))
        return model
    }

    override suspend fun <ME : Model<ModelId<Comparable<Any>>, *>> update(
        context: TransactionalContext,
        models: List<ME>
    ) {
        history.add(ModelsUpdated(context, models))
    }
}
