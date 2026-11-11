package com.razz.eva.uow

import com.razz.eva.domain.Principal

/**
 * Runs the first attempt of a new run of the unit of work this factory builds, as `UnitOfWorkExecutor.execute` does,
 * without the write. It takes the factory and the params lambda an `execute` call takes, builds the attempt's
 * context, the unit of work with that context and the params in the executor's order, runs `tryPerform` and applies
 * the executor's checks to the changes. Returns the changes, for `verifyInOrder`. A spec on an eva-test spec base
 * calls it without [executionContext]: `factory.firstAttempt(principal) { params }`.
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
 * Runs a retry of a new run, as `UnitOfWorkExecutor.execute` does after the first attempt's write failed: it builds
 * the first attempt's params first, then runs the second attempt of the same run as [firstAttempt] runs the first. The
 * first attempt's unit of work is not built and does not run. A spec on an eva-test spec base calls it without
 * [executionContext]: `factory.retry(principal) { params }`.
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
    val first = InstantiationContext.External(0, principal, run)
    try {
        first.params()
    } finally {
        first.end()
    }
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
    try {
        val uow = uowFactory(executionContext.copy(attempt = context))
        checkBuiltFor(uow, context)
        val changes = uow.tryPerform(principal, context.params())
        checkOutcome(uow, changes, uow.name())
        return changes
    } finally {
        context.end()
    }
}
