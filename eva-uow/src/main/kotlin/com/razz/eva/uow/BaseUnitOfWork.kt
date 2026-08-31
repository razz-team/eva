package com.razz.eva.uow

import com.razz.eva.domain.Principal
import com.razz.eva.persistence.PersistenceException
import com.razz.eva.uow.BaseUnitOfWork.Configuration.Companion.default
import com.razz.eva.uow.Retry.StaleRecordFixedRetry.Companion.DEFAULT
import java.time.InstantSource

/**
 * The template every unit of work variant instantiates. [C] is the receiver of the change block and
 * [BLOCK] is what the block must end on. The plain and composable [UnitOfWork]s pin [BLOCK] to
 * [RESULT]: their blocks end on the UoW result itself. [com.razz.eva.uow.composable.RegisteringUnitOfWork]
 * pins it to `Registered<RESULT>`, so a block whose last expression is a model that never went through
 * the DSL does not compile. Separating [BLOCK] from [RESULT] is what lets both families keep the one
 * name, `changes`: a suspend block with receiver erases to the same JVM signature whatever its return
 * type, so the two shapes cannot coexist as an overload or an override pair.
 */
abstract class BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, C, BLOCK>(
    executionContext: ExecutionContext,
    private val configuration: Configuration = default(),
) where PRINCIPAL : Principal<*>, PARAMS : UowParams<PARAMS>, RESULT : Any, C : Any, BLOCK : Any {

    protected val clock: InstantSource = executionContext.clock

    abstract suspend fun tryPerform(principal: PRINCIPAL, params: PARAMS): Changes<RESULT>

    open fun name(): String = this.javaClass.simpleName

    internal fun configuration(): Configuration = configuration

    open suspend fun onFailure(params: PARAMS, ex: PersistenceException): RESULT = throw ex

    private val NO_CHANGES: Changes<Unit> = RealisedChanges(Unit, listOf(), listOf())

    protected fun noChanges() = NO_CHANGES

    protected fun <R> noChanges(result: R): Changes<R> = RealisedChanges(result, listOf(), listOf())

    protected abstract suspend fun changes(init: suspend C.() -> BLOCK): Changes<RESULT>

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
