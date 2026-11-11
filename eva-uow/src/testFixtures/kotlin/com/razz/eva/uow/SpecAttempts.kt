package com.razz.eva.uow

import com.razz.eva.domain.Principal

/**
 * Runs the first attempt of a new run of the unit of work this factory builds, as `UnitOfWorkExecutor.execute` does,
 * without the write. It takes the factory and the params lambda an `execute` call takes, builds the attempt's
 * context, the unit of work with that context and the params in the executor's order, runs `tryPerform` and applies
 * the executor's checks to the changes. Returns the changes, for `verifyInOrder`. A spec on an eva-test spec base
 * calls it without [executionContext]: `factory.firstAttempt(principal) { params }`. The runner runs the unit of work
 * as the top unit of a run. A unit that runs only as a child and hands a model back for its parent to register fails
 * the executor's result check there; run it through its parent, or call `tryPerform` directly.
 */
suspend fun <P, PARAMS, RESULT, UOW> ((ExecutionContext) -> UOW).firstAttempt(
    executionContext: ExecutionContext,
    principal: P,
    params: InstantiationContext.() -> PARAMS,
): Changes<RESULT>
    where P : Principal<*>,
          PARAMS : UowParams<PARAMS>,
          RESULT : Any,
          UOW : BaseUnitOfWork<P, PARAMS, RESULT, *> =
    runAttempt(executionContext, this, principal, params, InstantiationContext.External(0, principal, newRun()))

/**
 * Runs a retry of a new run, as `UnitOfWorkExecutor.execute` does when the first attempt's write fails: it runs the
 * first attempt as [firstAttempt] does (its unit of work, its params, its block and the executor's checks) and
 * discards its changes, as the failed write leaves nothing. Then it runs the second attempt of the same run and
 * returns its changes. A failure in the first attempt is thrown, as the executor throws it. A spec on an eva-test
 * spec base calls it without [executionContext]: `factory.retry(principal) { params }`.
 */
suspend fun <P, PARAMS, RESULT, UOW> ((ExecutionContext) -> UOW).retry(
    executionContext: ExecutionContext,
    principal: P,
    params: InstantiationContext.() -> PARAMS,
): Changes<RESULT>
    where P : Principal<*>,
          PARAMS : UowParams<PARAMS>,
          RESULT : Any,
          UOW : BaseUnitOfWork<P, PARAMS, RESULT, *> {
    val run = newRun()
    runAttempt(executionContext, this, principal, params, InstantiationContext.External(0, principal, run))
    return runAttempt(executionContext, this, principal, params, InstantiationContext.External(1, principal, run))
}

private fun newRun() = InstantiationContext.Run()

private suspend fun <P, PARAMS, RESULT, UOW> runAttempt(
    executionContext: ExecutionContext,
    uowFactory: (ExecutionContext) -> UOW,
    principal: P,
    params: InstantiationContext.() -> PARAMS,
    context: InstantiationContext.External,
): Changes<RESULT>
    where P : Principal<*>,
          PARAMS : UowParams<PARAMS>,
          RESULT : Any,
          UOW : BaseUnitOfWork<P, PARAMS, RESULT, *> {
    val uow = uowFactory(executionContext.copy(attempt = context))
    checkBuiltFor(uow, context)
    val changes = uow.tryPerform(principal, context.params())
    checkOutcome(uow, changes, uow.name())
    return changes
}
