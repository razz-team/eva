package com.razz.eva.saga

import com.razz.eva.domain.Principal
import com.razz.eva.saga.Saga.Step
import com.razz.eva.saga.Saga.Terminal
import java.time.Duration
import java.util.UUID
import java.util.UUID.randomUUID

internal sealed interface SagaOutcome<out TERMINAL> {

    class Ended<TERMINAL>(val terminal: TERMINAL) : SagaOutcome<TERMINAL>

    class Restart(val cause: Exception) : SagaOutcome<Nothing>
}

@JvmInline
value class SagaRunId(private val id: UUID) {
    override fun toString() = id.toString()
    fun uuidValue() = id

    companion object {
        fun random() = SagaRunId(randomUUID())
    }
}

data class SagaRun<PRINCIPAL, PARAMS>(
    val id: SagaRunId,
    val parentId: SagaRunId?,
    val sagaName: String,
    val principal: PRINCIPAL,
    val params: PARAMS,
) where PRINCIPAL : Principal<*> {

    override fun toString() = "SagaRun[sagaName=$sagaName, id=$id, parentId=$parentId]"
}

interface SagaObserver<PRINCIPAL, PARAMS> where PRINCIPAL : Principal<*> {

    suspend fun onResumed(run: SagaRun<PRINCIPAL, PARAMS>, first: Step<*>)

    suspend fun onTransition(
        run: SagaRun<PRINCIPAL, PARAMS>,
        from: Step<*>,
        to: Step<*>,
        elapsed: Duration,
    )

    suspend fun onTerminated(
        run: SagaRun<PRINCIPAL, PARAMS>,
        terminal: Terminal<*>,
        elapsed: Duration,
    )

    suspend fun onFailed(
        run: SagaRun<PRINCIPAL, PARAMS>,
        step: Step<*>?,
        ex: Exception,
        mappedTo: Terminal<*>?,
    )
}
