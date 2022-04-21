package com.razz.eva.uow

import io.kotest.core.spec.style.BehaviorSpec

abstract class FunctionalSpec<M : PersistenceModule>(
    createTestModule: () -> M,
    body: FunctionalSpec<M>.() -> Unit
) : BehaviorSpec() {

    val module = createTestModule()

    init {
        body()
    }
}
