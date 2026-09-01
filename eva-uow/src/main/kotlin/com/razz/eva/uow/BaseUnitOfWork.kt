package com.razz.eva.uow

import com.razz.eva.domain.Model
import com.razz.eva.domain.Principal
import com.razz.eva.persistence.PersistenceException
import com.razz.eva.uow.BaseUnitOfWork.Configuration.Companion.default
import com.razz.eva.uow.Retry.StaleRecordFixedRetry.Companion.DEFAULT
import java.time.InstantSource

/**
 * The template every unit of work family instantiates. [C] documents the receiver of the change block
 * that the family's own `changes` builder accepts. The builder is deliberately not declared here:
 * block result types differ per family (a plain block ends on [RESULT], a proving block ends on
 * `Accounted<RESULT>`), a suspend block with receiver erases to the same JVM signature whatever its
 * return type, and nothing ever calls `changes` polymorphically, so a single overridable declaration
 * would buy nothing and force one shape on every family.
 */
abstract class BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, C>(
    executionContext: ExecutionContext,
    private val configuration: Configuration = default(),
) where PRINCIPAL : Principal<*>, PARAMS : UowParams<PARAMS>, RESULT : Any, C : Any {

    protected val clock: InstantSource = executionContext.clock

    abstract suspend fun tryPerform(principal: PRINCIPAL, params: PARAMS): Changes<RESULT>

    open fun name(): String = this.javaClass.simpleName

    internal fun configuration(): Configuration = configuration

    open suspend fun onFailure(params: PARAMS, ex: PersistenceException): RESULT = throw ex

    private val NO_CHANGES: Changes<Unit> = RealisedChanges(Unit, listOf(), listOf())

    protected fun noChanges() = NO_CHANGES

    protected fun <R> noChanges(result: R): Changes<R> {
        requireNoDroppedWrite(result, "noChanges")
        return RealisedChanges(result, listOf(), listOf())
    }

    protected fun <R> Changes<R>.result(): R = this.result

    data class Configuration(
        val retry: Retry? = DEFAULT,
        val supportsOutOfOrderPersisting: Boolean = false,
        val returnRoundtrippedModels: Boolean = true,
        val writeTxScope: WriteTxScope = WriteTxScope.FLUSH,
    ) {
        companion object {
            fun default() = Configuration()
        }
    }
}

/**
 * A new or dirty model in [result] is about to leave a UoW without going through the changes DSL:
 * whatever mutation it carries will never be persisted. Rejecting it here turns the silent write drop
 * into a loud failure at the site that dropped it.
 */
internal fun requireNoDroppedWrite(result: Any?, site: String) {
    val models = when (result) {
        is Model<*, *> -> listOf(result)
        is Collection<*> -> result.filterIsInstance<Model<*, *>>()
        else -> listOf()
    }
    for (model in models) {
        require(!model.isNew() && !model.isDirty()) {
            "Attempted to pass ${if (model.isNew()) "new" else "changed"} " +
                "model [${model.id().stringValue()}] to $site: the write would be silently dropped"
        }
    }
}
