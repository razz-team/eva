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

    abstract suspend fun tryPerform(principal: PRINCIPAL, params: PARAMS): ChangesWithResult<RESULT>

    open fun name(): String = this.javaClass.simpleName

    internal fun configuration(): Configuration = configuration

    internal fun clock(): Clock = clock

    open suspend fun onFailure(params: PARAMS, ex: PersistenceException): RESULT = throw ex

    protected val NO_CHANGES: ChangesWithResult<Unit> = NoChanges

    // TODO do we actually need that ?
    protected fun <R> notChanged(result: R): ChangesWithResult<R> = DefaultChangesWithResult(result, emptyList())

    protected suspend fun changes(init: suspend ChangesDsl.() -> RESULT): ChangesWithResult<RESULT> {
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
