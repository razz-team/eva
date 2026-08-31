package com.razz.eva.uow

import com.razz.eva.domain.Principal

/**
 * A [UnitOfWork] whose change block must end on a [Registered] value. `changes { }` persists only what
 * went through `add` / `update` / `notChanged`, so a plain UoW that returns a freshly mutated model
 * without registering it drops the write on the floor and reports success. Here that branch does not
 * compile: `changes` takes a block returning `Registered<RESULT>`, and the only mints are
 * [RegisteringChangesDsl]'s registrations and the explicit [resultOnly].
 *
 * Nothing else moves. `tryPerform` keeps the name, the parameters and the return type the base class
 * gives it, the block keeps the DSL's own names and its own shape, and the block builder keeps the name
 * `changes`, so adopting this on a UoW is one word at the top of the class.
 *
 * Secondary models still go through the same `add` / `update` and can still be forgotten. The evidence
 * covers the result path, which is where the forgotten registrations happen
 * (`return@changes parent` instead of `add(parent)`).
 */
abstract class RegisteringUnitOfWork<PRINCIPAL, PARAMS, RESULT>(
    executionContext: ExecutionContext,
    configuration: Configuration = Configuration.default(),
) : BaseUnitOfWork<PRINCIPAL, PARAMS, RESULT, RegisteringChangesDsl, Registered<RESULT>>(
    executionContext,
    configuration,
) where PRINCIPAL : Principal<*>, PARAMS : UowParams<PARAMS>, RESULT : Any {

    final override suspend fun changes(
        init: suspend RegisteringChangesDsl.() -> Registered<RESULT>,
    ): Changes<RESULT> {
        return ChangesDsl.changes(ChangesAccumulator()) { RegisteringChangesDsl(this).init().result }
    }
}
