package com.razz.eva.saga

import com.razz.eva.domain.Principal
import java.util.UUID
import java.util.UUID.randomUUID

@JvmInline
value class SagaRunId(private val id: UUID) {
    override fun toString() = id.toString()

    fun uuidValue() = id

    companion object {
        fun random() = SagaRunId(randomUUID())
    }
}

@JvmInline
value class SagaAttemptId(private val id: UUID) {
    override fun toString() = id.toString()

    fun uuidValue() = id

    companion object {
        fun random() = SagaAttemptId(randomUUID())
    }
}

data class SagaRun<PRINCIPAL, PARAMS> private constructor(
    val runId: SagaRunId,
    val attemptId: SagaAttemptId,
    val parentAttemptId: SagaAttemptId?,
    val attempt: Int,
    val sagaName: String,
    val principal: PRINCIPAL,
    val params: PARAMS,
) where PRINCIPAL : Principal<*> {

    fun restarted(): SagaRun<PRINCIPAL, PARAMS> {
        return copy(
            attemptId = SagaAttemptId.random(),
            parentAttemptId = attemptId,
            attempt = attempt + 1,
        )
    }

    override fun toString() =
        "SagaRun[sagaName=$sagaName, runId=$runId, attemptId=$attemptId, " +
            "parentAttemptId=$parentAttemptId, attempt=$attempt]"

    companion object {
        fun <PRINCIPAL : Principal<*>, PARAMS> first(
            sagaName: String,
            principal: PRINCIPAL,
            params: PARAMS,
        ): SagaRun<PRINCIPAL, PARAMS> {
            return SagaRun(
                runId = SagaRunId.random(),
                attemptId = SagaAttemptId.random(),
                parentAttemptId = null,
                attempt = 0,
                sagaName = sagaName,
                principal = principal,
                params = params,
            )
        }
    }
}
