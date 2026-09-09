package com.razz.eva.uow.proving.unit

/**
 * The effect-shaped proving UoW: for a `Unit` result the tail evidence would certify nothing, so the
 * block is free-form and the `Unit` type argument disappears with it. Adoption is an import plus
 * dropping `Unit` from the declaration.
 */
typealias UnitOfWork<PRINCIPAL, PARAMS> =
    com.razz.eva.uow.composable.ProvingEffectUnitOfWork<PRINCIPAL, PARAMS>
