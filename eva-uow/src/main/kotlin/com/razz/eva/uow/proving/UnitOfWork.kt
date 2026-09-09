package com.razz.eva.uow.proving

/**
 * Adoption is an import. A UoW extending `com.razz.eva.uow.UnitOfWork` or
 * `com.razz.eva.uow.composable.UnitOfWork` moves to the proving family by swapping that import for
 * this one: the declaration keeps reading `UnitOfWork<PRINCIPAL, PARAMS, RESULT>`, and what changes
 * is what the compiler now demands of the change block.
 */
typealias UnitOfWork<PRINCIPAL, PARAMS, RESULT> =
    com.razz.eva.uow.composable.ProvingUnitOfWork<PRINCIPAL, PARAMS, RESULT>
