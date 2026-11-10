package com.razz.eva.uow.composable

import com.razz.eva.domain.CreatableEntity
import com.razz.eva.domain.DeletableEntity
import com.razz.eva.domain.EntityKey
import com.razz.eva.domain.Model
import com.razz.eva.domain.UpdatableEntity
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.domain.Principal
import com.razz.eva.uow.OtelAttributes.MODEL_ID
import com.razz.eva.tracing.getEvaTracer
import com.razz.eva.tracing.use
import com.razz.eva.uow.AddModel
import com.razz.eva.uow.PersistedLookup
import com.razz.eva.uow.Changes
import com.razz.eva.uow.ChangesAccumulator
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.InstantiationContext
import com.razz.eva.uow.NoopModel
import com.razz.eva.uow.UpdateModel
import com.razz.eva.uow.UowParams
import io.opentelemetry.api.trace.Span
import kotlin.reflect.KClass

class ChangesDsl internal constructor(
    private val executionContext: ExecutionContext,
) {
    private var changes: ChangesAccumulator = executionContext.inheritedChanges ?: ChangesAccumulator()
    private val inheritedModelIds: MutableSet<ModelId<out Comparable<*>>> = changes.modelIds().toMutableSet()
    // var: assigned by roundtrip { } mid-block; null means no roundtrip, so the executor default-roundtrips.
    private var resultBuilder: ((PersistedLookup) -> Any?)? = null

    private fun <R> withResult(result: R): Changes<R> = changes.withResult(result, resultBuilder)

    /**
     * Builds the UoW result via [p], which resolves each model to its change-set instance by id. Top-level
     * execution reruns this over the persisted (flushed) set, so the result carries DB-roundtripped models,
     * including ones registered by composed child UoWs. The value built eagerly here (in-memory) is the seed
     * returned under composition, where only the top-level UoW's builder is rerun.
     */
    fun <R> roundtrip(build: (p: PersistedLookup) -> R): R {
        resultBuilder = build
        return build(PersistedLookup { changes.changeFor(it)?.model })
    }

    fun <MID, E, M> add(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        require(model.isNew()) {
            "Attempted to register ${if (model.isDirty()) "changed" else "unchanged"} " +
                "model [${model.id().stringValue()}] as new"
        }
        changes = changes.withAddedModel(model)
        return model
    }

    fun <MID, E, M> update(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        val existing = changes.changeFor(model.id())
        if (existing != null && model.id() in inheritedModelIds) {
            val newEvents = model.modelEvents()
            check(newEvents isSuccessorOf existing.modelEvents) {
                if (newEvents isSameAs existing.modelEvents) {
                    "No-op update for model [${model.id().stringValue()}]: no new events on top of the " +
                        "existing change. Use notChanged(...) or guard update(...)."
                } else {
                    "Failed to merge changes for model [${model.id().stringValue()}]"
                }
            }
            val merged = when (existing) {
                is AddModel<*, *, *> -> AddModel(model, newEvents)
                is UpdateModel<*, *, *> -> UpdateModel(model, newEvents)
                is NoopModel -> UpdateModel(model, newEvents)
            }
            changes = changes.withReplacedModelChange(model.id(), merged)
        } else {
            require(model.isDirty()) {
                "Attempted to register ${if (model.isNew()) "new" else "unchanged"} " +
                    "model [${model.id().stringValue()}] as changed"
            }
            changes = changes.withUpdatedModel(model)
        }
        return model
    }

    fun <MID, E, M> notChanged(model: M): M
        where M : Model<MID, E>, E : ModelEvent<MID>, MID : ModelId<out Comparable<*>> {
        val existing = changes.changeFor(model.id())
        if (existing == null || model.id() !in inheritedModelIds) {
            require(model.isPersisted()) {
                "Attempted to register ${if (model.isNew()) "new" else "changed"} " +
                    "model [${model.id().stringValue()}] as unchanged"
            }
            changes = changes.withUnchangedModel(model)
        }
        return model
    }

    fun <E : CreatableEntity> add(entity: E): E {
        changes = changes.withAddedEntity(entity)
        return entity
    }

    fun <E : UpdatableEntity> update(entity: E): E {
        changes = changes.withUpdatedEntity(entity)
        return entity
    }

    fun <E : DeletableEntity> delete(entity: E): E {
        changes = changes.withDeletedEntity(entity)
        return entity
    }

    inline fun <reified E : DeletableEntity, K : EntityKey<E>> delete(key: K): K {
        deleteByKeyInternal(key, E::class)
        return key
    }

    @PublishedApi
    internal fun <E : DeletableEntity, K : EntityKey<E>> deleteByKeyInternal(key: K, entityClass: KClass<E>) {
        changes = changes.withDeletedEntityByKey(key, entityClass)
    }

    /**
     * Runs the child unit of work that [uowFactory] builds. The child starts from the changes this block has
     * registered so far, and the changes it returns replace them. Throws if the returned changes lost a model change or
     * an entity change this block had registered; the block's changes then stay as they were before the child ran.
     */
    suspend fun <PRINCIPAL, PARAMS, RESULT, UOW> execute(
        uowFactory: (ExecutionContext) -> UOW,
        principal: PRINCIPAL,
        params: InstantiationContext.Internal.() -> PARAMS,
    ): RESULT
        where PRINCIPAL : Principal<*>,
              PARAMS : UowParams<PARAMS>,
              RESULT : Any,
              UOW : UnitOfWork<PRINCIPAL, PARAMS, RESULT> = merge(runChild(uowFactory, principal, params))

    /**
     * Runs a child unit of work, in a span of its own, and returns the changes it built. It does not merge them into
     * this block's changes: [merge] does that.
     */
    internal suspend fun <PRINCIPAL, PARAMS, RESULT, UOW> runChild(
        uowFactory: (ExecutionContext) -> UOW,
        principal: PRINCIPAL,
        params: InstantiationContext.Internal.() -> PARAMS,
    ): ChildRun<RESULT>
        where PRINCIPAL : Principal<*>,
              PARAMS : UowParams<PARAMS>,
              RESULT : Any,
              UOW : UnitOfWork<PRINCIPAL, PARAMS, RESULT> {
        val parentChanges = changes
        val uow = uowFactory(executionContext.withInheritedChanges(parentChanges))
        val span = uowSpan(uow.name())
        val context = InstantiationContext.Internal(0, principal, InstantiationContext.Run())
        return span.use {
            val subChanges = performingSpan(uow.name()).use {
                uow.tryPerform(principal, params(context))
            }
            span.setAttribute(
                MODEL_ID,
                subChanges.modelChangesToPersist.map { it.id.stringValue() },
            )
            ChildRun(uow.name(), parentChanges, subChanges)
        }
    }

    /**
     * Merges the changes a child built into this block's changes and returns the child's result. Throws, and merges
     * nothing, if the child's changes lost a change this block had registered. If the child registered no change,
     * this block's changes stay as they are.
     */
    internal fun <RESULT> merge(run: ChildRun<RESULT>): RESULT {
        val subChanges = run.changes
        if (subChanges.modelChangesToPersist.isNotEmpty() || subChanges.entityChangesToPersist.isNotEmpty()) {
            val merged = ChangesAccumulator.from(subChanges)
            merged.checkKeepsAllOf(changes, run.uowName)
            changes = merged
            inheritedModelIds.addAll(changes.modelIds())
        }
        // The parent gets the result the child built in memory. The child's resultBuilder does not run again.
        return subChanges.result
    }

    /**
     * The outcome of [runChild]: the child's name, this block's changes when the child started ([parentChanges]), and
     * the changes the child returned.
     */
    internal class ChildRun<RESULT>(
        val uowName: String,
        val parentChanges: ChangesAccumulator,
        val changes: Changes<RESULT>,
    )

    companion object {
        internal suspend inline fun <R> changes(
            executionContext: ExecutionContext,
            @Suppress("REDUNDANT_INLINE_SUSPEND_FUNCTION_TYPE")
            init: suspend ChangesDsl.() -> R,
        ): Changes<R> {
            val dsl = ChangesDsl(executionContext)
            val res = init(dsl)
            return dsl.withResult(res)
        }
    }

    private fun ChangesAccumulator.checkKeepsAllOf(parent: ChangesAccumulator, uowName: String) {
        val droppedModelIds = modelIdsDroppedFrom(parent)
        val keepsEntityChanges = keepsEntityChangesOf(parent)
        check(droppedModelIds.isEmpty() && keepsEntityChanges) {
            val dropped = listOfNotNull(
                droppedModelIds.takeIf { it.isNotEmpty() }?.let { ids -> "models ${ids.map { it.stringValue() }}" },
                "entity changes".takeUnless { keepsEntityChanges },
            )
            "Composed unit of work [$uowName] returned changes without ${dropped.joinToString(" and ")} that its " +
                "parent had registered. Build the child's changes with changes { }, in a unit of work created with " +
                "the ExecutionContext its factory receives."
        }
    }

    private fun uowSpan(name: String): Span = executionContext.otel.getEvaTracer()
        .spanBuilder(name)
        .startSpan()

    private fun performingSpan(name: String): Span = executionContext.otel.getEvaTracer()
        .spanBuilder("$name-perform")
        .startSpan()

    private infix fun List<ModelEvent<*>>
        .isSuccessorOf(events: List<ModelEvent<*>>): Boolean {
        if (size <= events.size) {
            return false
        }
        events.forEachIndexed { i, e ->
            if (this[i] !== e) {
                return false
            }
        }
        return true
    }

    private infix fun List<ModelEvent<*>>
        .isSameAs(events: List<ModelEvent<*>>): Boolean {
        if (size != events.size) {
            return false
        }
        events.forEachIndexed { i, e ->
            if (this[i] !== e) {
                return false
            }
        }
        return true
    }
}
