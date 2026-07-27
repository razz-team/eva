package com.razz.eva.uow

import com.razz.eva.domain.Principal
import com.razz.eva.persistence.PersistenceException
import com.razz.eva.uow.BaseUnitOfWork.Configuration.Companion.default
import com.razz.eva.uow.Retry.StaleRecordFixedRetry.Companion.DEFAULT
import java.time.InstantSource

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

    /**
     * Signals that this UoW mutated no models: the executor persists no model/entity changes but still
     * writes a uow_event, recording the invocation in the audit trail (and honoring any idempotency key).
     * Use [abstain] instead when even that empty event is unwanted.
     */
    protected fun noChanges() = NO_CHANGES

    /**
     * Same as [noChanges] but carries a [result] to return to the caller. The empty uow_event is still
     * written; use [abstain] with the same [result] to skip it entirely.
     */
    protected fun <R> noChanges(result: R): Changes<R> = RealisedChanges(result, listOf(), listOf())

    private val ABSTAINED: Changes<Unit> = Abstained(Unit)

    /**
     * Like [noChanges] but additionally skips writing the uow_event: the executor returns without opening
     * a persistence transaction at all. Use on the "get" branch of a get-or-create UoW, where the model
     * already existed and no side effect was performed, to avoid an empty uow_event per call. Because no
     * event is written, no idempotency key is recorded for the invocation.
     */
    protected fun abstain() = ABSTAINED

    /**
     * Same as [abstain] but carries a [result] to return to the caller. No uow_event is written and no
     * persistence transaction is opened, so no idempotency key is recorded for the invocation.
     */
    protected fun <R> abstain(result: R): Changes<R> = Abstained(result)

    protected abstract suspend fun changes(init: suspend C.() -> RESULT): Changes<RESULT>

    protected fun <R> Changes<R>.result(): R = this.result

    data class Configuration(
        val retry: Retry? = DEFAULT,
        val supportsOutOfOrderPersisting: Boolean = false,
        val returnRoundtrippedModels: Boolean = true,
    ) {
        companion object {
            fun default() = Configuration()
        }
    }
}
