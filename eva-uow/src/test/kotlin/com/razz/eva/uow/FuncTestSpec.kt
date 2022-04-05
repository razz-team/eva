package com.razz.eva.uow

import com.razz.eva.persistence.config.ExecutorType
import io.kotest.core.spec.style.BehaviorSpec

abstract class FuncTestSpec<M : PersistenceModule>(
    createTestModule: () -> M,
    body: FuncTestSpec<M>.() -> Unit
) : BehaviorSpec() {

    val testModule = createTestModule()

    init {
        body()
    }

    companion object {
        val executorType: ExecutorType
            get() = System
                .getenv("PRIMARY_EXECUTOR_TYPE")
                ?.runCatching(ExecutorType::valueOf)
                ?.getOrNull()
                ?: ExecutorType.JDBC
    }
}
