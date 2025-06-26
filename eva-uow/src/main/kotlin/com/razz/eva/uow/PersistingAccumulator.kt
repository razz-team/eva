package com.razz.eva.uow

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelId
import com.razz.eva.repository.ModelRepos
import com.razz.eva.repository.TransactionalContext
import com.razz.eva.uow.PersistingAccumulator.FlushOperation
import kotlin.reflect.KClass

internal sealed interface PersistingAccumulator : ModelPersisting {

    fun interface FlushOperation {
        suspend operator fun invoke(context: TransactionalContext): List<Model<*, *>>
    }

    fun adhoc(block: suspend () -> Unit)

    fun accumulated(): List<FlushOperation>

    class ChangesAccumulator(private val repos: ModelRepos) : PersistingAccumulator {
        private val changes: MutableList<FlushOperation> = mutableListOf()

        override fun <ID : ModelId<out Comparable<*>>, M : Model<ID, *>> add(model: M) {
            changes.add { context -> repos.repoFor(model).add(context, model).let(::listOf) }
        }

        override fun <ID : ModelId<out Comparable<*>>, M : Model<ID, *>> update(model: M) {
            changes.add { context -> repos.repoFor(model).update(context, model).let(::listOf) }
        }

        override fun adhoc(block: suspend () -> Unit) {
            changes.add { context ->
                block()
                listOf()
            }
        }

        override fun accumulated() = changes
    }

    class BatchesAccumulator(private val repos: ModelRepos) : PersistingAccumulator {
        private val updates: MutableMap<KClass<out Model<*, *>>, Batch.Update<*, *>> = mutableMapOf()
        private val inserts: MutableMap<KClass<out Model<*, *>>, Batch.Add<*, *>> = mutableMapOf()
        private val adhocs: MutableList<FlushOperation> = mutableListOf()

        override fun <ID : ModelId<out Comparable<*>>, M : Model<ID, *>> add(model: M) {
            inserts.compute(model::class) { _, v ->
                v?.with(model) ?: Batch.Add(model)
            }
        }

        override fun <ID : ModelId<out Comparable<*>>, M : Model<ID, *>> update(model: M) {
            updates.compute(model::class) { _, v ->
                v?.with(model) ?: Batch.Update(model)
            }
        }

        override fun adhoc(block: suspend () -> Unit) {
            adhocs.add { context ->
                block()
                listOf()
            }
        }

        override fun accumulated() = listOf(updates.values, inserts.values).flatMap {
            it.map { b -> FlushOperation { context -> b.persist(context, repos) } }
        } + adhocs
    }

    companion object Factory {
        fun newPersistingAccumulator(
            doBatching: Boolean,
            repos: ModelRepos
        ): PersistingAccumulator = if (doBatching) {
            BatchesAccumulator(repos)
        } else {
            ChangesAccumulator(repos)
        }
    }
}
