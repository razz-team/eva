package com.razz.eva.test.uow

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelId
import com.razz.eva.domain.Principal
import com.razz.eva.uow.BaseUnitOfWork
import com.razz.eva.uow.Changes
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.InstantiationContext
import com.razz.eva.uow.ModelParam
import com.razz.eva.uow.UowParams
import com.razz.eva.uow.firstAttempt
import com.razz.eva.uow.retry
import io.opentelemetry.api.OpenTelemetry
import java.time.Clock

interface UowSpecBase {

    /** The context the spec builds its units of work with. */
    val executionContext: ExecutionContext

    fun <MID : ModelId<out Comparable<*>>, M : Model<MID, *>> constantModelParam(
        model: M,
    ): ModelParam<MID, M> {
        return com.razz.eva.uow.TestConstantModelParam.constantModelParamForSpec(model)
    }

    fun executionContext(clock: Clock, otel: OpenTelemetry): ExecutionContext {
        return com.razz.eva.uow.TestExecutionContext.executionContextForSpec(clock, otel)
    }

    /**
     * Runs the first attempt of a new run of the unit of work this factory builds, as `UnitOfWorkExecutor.execute`
     * does, without the write: `{ ctx: ExecutionContext -> MyUow(ctx, ...) }.firstAttempt(principal) { Params(...) }`.
     * The factory and the params lambda are the ones an `execute` call takes. The unit of work and its params share
     * the attempt, and the executor's checks run on the changes, which it returns for `verifyInOrder`.
     */
    suspend fun <P, PARAMS, RESULT, UOW> ((ExecutionContext) -> UOW).firstAttempt(
        principal: P,
        params: InstantiationContext.() -> PARAMS,
    ): Changes<RESULT>
        where P : Principal<*>,
              PARAMS : UowParams<PARAMS>,
              RESULT : Any,
              UOW : BaseUnitOfWork<P, PARAMS, RESULT, *> =
        firstAttempt(executionContext, principal, params)

    /**
     * Runs a retry of a new run, as `UnitOfWorkExecutor.execute` does after the first attempt's write failed: it builds
     * the first attempt's params with the same lambda, then runs the second attempt as [firstAttempt] runs the first.
     * A `ModelParam` loads its model again, and a guarded unit of work reloads each proven model.
     */
    suspend fun <P, PARAMS, RESULT, UOW> ((ExecutionContext) -> UOW).retry(
        principal: P,
        params: InstantiationContext.() -> PARAMS,
    ): Changes<RESULT>
        where P : Principal<*>,
              PARAMS : UowParams<PARAMS>,
              RESULT : Any,
              UOW : BaseUnitOfWork<P, PARAMS, RESULT, *> =
        retry(executionContext, principal, params)
}
