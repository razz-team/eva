package com.razz.eva.uow

import com.razz.eva.domain.Model
import com.razz.eva.persistence.WithCtxConnectionTransactionManager
import com.razz.eva.repository.ModelRepos
import com.razz.eva.repository.ModelRepository
import com.razz.eva.repository.hasRepo
import com.razz.eva.uow.ExecutionStep.TransactionFinished
import com.razz.eva.uow.ExecutionStep.TransactionStarted
import com.razz.eva.uow.func.SpyRepo
import kotlin.reflect.KClass

internal object FakeMemorizingPersisting {

    operator fun invoke(vararg uows: KClass<out Model<*, *>>): Persisting {
        val history = mutableListOf<ExecutionStep>()
        val txnManager = WithCtxConnectionTransactionManager(
            beforeTxn = { history.add(TransactionStarted(it)) },
            afterTxn = { mode, _ -> history.add(TransactionFinished(mode)) }
        )
        val repos = uows.map { it hasRepo anyRepo(history) }.toTypedArray()
        val persisting = Persisting(txnManager, ModelRepos(*repos), DummyEventRepository())
        executions[persisting] = history
        return persisting
    }

    private val executions: MutableMap<Persisting, MutableList<ExecutionStep>> = mutableMapOf()

    fun executionHistory(persisting: Persisting): List<ExecutionStep> {
        return executions.getValue(persisting)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <M : Model<*, *>> anyRepo(history: MutableList<ExecutionStep>) =
        SpyRepo(history) as ModelRepository<*, *, M>
}
