package com.razz.eva.uow

import com.razz.eva.domain.CreatableEntity
import com.razz.eva.domain.DeletableEntity
import com.razz.eva.domain.EntityKey
import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelId
import com.razz.eva.repository.EntityRepos
import com.razz.eva.repository.ModelRepos
import com.razz.eva.repository.TransactionalContext
import kotlin.reflect.KClass

internal sealed interface PersistingAccumulator : ModelPersisting, EntityPersisting {

    fun interface FlushOperation {
        suspend operator fun invoke(context: TransactionalContext): List<Model<*, *>>
    }

    fun accumulated(): List<FlushOperation>

    class ChangesAccumulator(
        private val modelRepos: ModelRepos,
        private val entityRepos: EntityRepos,
    ) : PersistingAccumulator {
        private val changes: MutableList<FlushOperation> = mutableListOf()

        override fun <ID : ModelId<out Comparable<*>>, M : Model<ID, *>> add(model: M) {
            changes.add { context -> modelRepos.repoFor(model).add(context, model).let(::listOf) }
        }

        override fun <ID : ModelId<out Comparable<*>>, M : Model<ID, *>> update(model: M) {
            changes.add { context -> modelRepos.repoFor(model).update(context, model).let(::listOf) }
        }

        override fun <E : CreatableEntity> add(entity: E) {
            changes.add { context ->
                entityRepos.repoFor(entity).add(context, entity)
                listOf()
            }
        }

        override fun <E : DeletableEntity> delete(entity: E) {
            changes.add { context ->
                entityRepos.deletableRepoFor(entity).delete(context, entity)
                listOf()
            }
        }

        override fun <E : DeletableEntity, K : EntityKey<E>> delete(key: K, entityClass: KClass<E>) {
            changes.add { context ->
                entityRepos.keyDeletableRepoFor(entityClass).delete(context, key)
                listOf()
            }
        }

        override fun accumulated() = changes
    }

    class BatchesAccumulator(
        private val modelRepos: ModelRepos,
        private val entityRepos: EntityRepos,
    ) : PersistingAccumulator {
        private val updates: MutableMap<KClass<out Model<*, *>>, ModelBatch.Update<*, *>> = mutableMapOf()
        private val inserts: MutableMap<KClass<out Model<*, *>>, ModelBatch.Add<*, *>> = mutableMapOf()
        private val entityInserts: MutableMap<KClass<out CreatableEntity>, EntityBatch.Add<*>> = mutableMapOf()
        private val entityDeletes: MutableMap<KClass<out DeletableEntity>, EntityBatch.Delete<*>> = mutableMapOf()
        private val entityKeyDeletes: MutableMap<KClass<out DeletableEntity>, EntityBatch.DeleteByKey<*, *>> =
            mutableMapOf()

        override fun <ID : ModelId<out Comparable<*>>, M : Model<ID, *>> add(model: M) {
            inserts.compute(model::class) { _, v ->
                v?.with(model) ?: ModelBatch.Add(model)
            }
        }

        override fun <ID : ModelId<out Comparable<*>>, M : Model<ID, *>> update(model: M) {
            updates.compute(model::class) { _, v ->
                v?.with(model) ?: ModelBatch.Update(model)
            }
        }

        override fun <E : CreatableEntity> add(entity: E) {
            entityInserts.compute(entity::class) { _, v ->
                v?.with(entity) ?: EntityBatch.Add(entity)
            }
        }

        override fun <E : DeletableEntity> delete(entity: E) {
            entityDeletes.compute(entity::class) { _, v ->
                v?.with(entity) ?: EntityBatch.Delete(entity)
            }
        }

        override fun <E : DeletableEntity, K : EntityKey<E>> delete(key: K, entityClass: KClass<E>) {
            entityKeyDeletes.compute(entityClass) { _, v ->
                v?.withKey(key) ?: EntityBatch.DeleteByKey(key, entityClass)
            }
        }

        override fun accumulated(): List<FlushOperation> {
            val modelOps = listOf(updates.values, inserts.values).flatMap {
                it.map { b -> FlushOperation { context -> b.persist(context, modelRepos) } }
            }
            val entityOps = listOf(entityInserts.values, entityDeletes.values, entityKeyDeletes.values).flatMap {
                it.map { b ->
                    FlushOperation { context ->
                        b.persist(context, entityRepos)
                        listOf()
                    }
                }
            }
            return modelOps + entityOps
        }
    }

    companion object Factory {
        fun newPersistingAccumulator(
            doBatching: Boolean,
            modelRepos: ModelRepos,
            entityRepos: EntityRepos,
        ): PersistingAccumulator = if (doBatching) {
            BatchesAccumulator(modelRepos, entityRepos)
        } else {
            ChangesAccumulator(modelRepos, entityRepos)
        }
    }
}
