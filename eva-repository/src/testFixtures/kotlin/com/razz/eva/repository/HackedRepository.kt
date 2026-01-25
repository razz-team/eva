package com.razz.eva.repository

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.jooq.record.TypedStatefulModelRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jooq.DSLContext
import org.jooq.Table
import kotlin.coroutines.EmptyCoroutineContext

class PreModifyCallback<ID : Comparable<ID>, MID : ModelId<ID>, M : Model<MID, *>> : suspend (M) -> Unit {

    private val actions = mutableMapOf<MID, suspend () -> Unit>()

    fun onPreUpdate(modelId: MID, action: suspend () -> Unit) {
        actions[modelId] = action
    }

    override suspend fun invoke(model: M) {
        actions[model.id()]?.invoke()
    }
}

abstract class HackedRepository<ID, MID, M, ME, R, S>(
    queryExecutor: QueryExecutor,
    dslContext: DSLContext,
    table: Table<R>,
    private val preUpdate: PreModifyCallback<ID, MID, M>
) : JooqStatefulModelRepository<ID, MID, M, ME, R, S>(
    queryExecutor = queryExecutor,
    dslContext = dslContext,
    table = table,
    stripNotModifiedFields = true,
) where ID : Comparable<ID>,
        MID : ModelId<ID>,
        ME : ModelEvent<MID>,
        M : Model<MID, ME>,
        R : TypedStatefulModelRecord<ID, S>,
        S : Enum<S> {

    private val detachedScope: CoroutineScope = CoroutineScope(EmptyCoroutineContext)

    override suspend fun <ME : M> update(context: TransactionalContext, model: ME): ME {
        detachedScope.launch {
            preUpdate(model)
        }.join()
        return super.update(context, model)
    }

    override suspend fun <ME : M> update(context: TransactionalContext, models: List<ME>): List<ME> {
        detachedScope.launch {
            models.forEach { model ->
                preUpdate(model)
            }
        }.join()
        return super.update(context, models)
    }
}
