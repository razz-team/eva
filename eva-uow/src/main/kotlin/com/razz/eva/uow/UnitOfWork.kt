package com.razz.eva.uow

import com.razz.eva.domain.Principal
import com.razz.eva.persistence.PersistenceException
import com.razz.eva.uow.BaseUnitOfWork.Configuration.Companion.default
import com.razz.eva.uow.Retry.StaleRecordFixedRetry.Companion.DEFAULT
import java.time.Clock

abstract class BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, C>(
    protected val clock: Clock,
    private val configuration: Configuration = default()
) where PRINCIPAL : Principal<*>, PARAMS : UowParams<PARAMS>, RESULT : Any, C : Any {

    abstract suspend fun tryPerform(principal: PRINCIPAL, params: PARAMS): Changes<RESULT>

    open fun name(): String = this.javaClass.simpleName

    internal fun configuration(): Configuration = configuration

    internal fun clock(): Clock = clock

    open suspend fun onFailure(params: PARAMS, ex: PersistenceException): RESULT = throw ex

    private val NO_CHANGES: Changes<Unit> = DefaultChanges(Unit, emptyList())

    protected fun noChanges() = NO_CHANGES

    protected fun <R> noChanges(result: R): Changes<R> = DefaultChanges(result, emptyList())

    protected abstract suspend fun changes(init: suspend C.() -> RESULT): Changes<RESULT>

    protected fun <R> Changes<R>.result(): R = this.result

    data class Configuration(
        val retry: Retry? = DEFAULT,
        val supportsOutOfOrderPersisting: Boolean = false
    ) {
        companion object {
            fun default() = Configuration()
        }
    }
}

abstract class UnitOfWork<PRINCIPAL, PARAMS, RESULT>(
    clock: Clock,
    configuration: Configuration = default()
) : BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, ChangesDsl>(clock, configuration)
    where PRINCIPAL : Principal<*>, PARAMS : UowParams<PARAMS>, RESULT : Any {

    final override suspend fun changes(init: suspend ChangesDsl.() -> RESULT): Changes<RESULT> {
        return ChangesDsl.changes(ChangesWithoutResult(), init)
    }
}
