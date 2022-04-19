package com.razz.eva.uow

import com.razz.eva.persistence.PersistenceException
import com.razz.eva.uow.Retry.StaleRecordFixedRetry.Companion.DEFAULT
import com.razz.eva.uow.UnitOfWork.Configuration.Companion.default
import com.razz.eva.uow.params.UowParams
import java.time.Clock

abstract class UnitOfWork<PRINCIPAL, PARAMS, RESULT>(
    protected val clock: Clock,
    private val configuration: Configuration = default()
) where PRINCIPAL : Principal<*>, PARAMS : UowParams<PARAMS>, RESULT : Any {

    abstract suspend fun tryPerform(principal: PRINCIPAL, params: PARAMS): Changes<RESULT>

    open fun name(): String = this.javaClass.simpleName

    internal fun configuration(): Configuration = configuration

    internal fun clock(): Clock = clock

    open suspend fun onFailure(params: PARAMS, ex: PersistenceException): RESULT = throw ex

    // TODO do we actually need that ?
    protected val NO_CHANGES: Changes<Unit> = DefaultChanges(Unit, emptyList())

    protected fun noChanges() = NO_CHANGES

    // TODO do we actually need that ?
    protected fun <R> noChanges(result: R): Changes<R> = DefaultChanges(result, emptyList())

    protected suspend fun changes(init: suspend ChangesDsl.() -> RESULT): Changes<RESULT> {
        return ChangesDsl.changes(ChangesWithoutResult(), init)
    }

    data class Configuration(
        val retry: Retry? = DEFAULT,
        val supportsOutOfOrderPersisting: Boolean = false
    ) {
        companion object {
            fun default() = Configuration()
        }
    }
}
