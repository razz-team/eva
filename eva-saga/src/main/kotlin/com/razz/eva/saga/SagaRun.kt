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

data class SagaRun<PRINCIPAL, PARAMS>(
    val id: SagaRunId,
    val parentId: SagaRunId?,
    val attempt: Int,
    val sagaName: String,
    val principal: PRINCIPAL,
    val params: PARAMS,
) where PRINCIPAL : Principal<*> {

    override fun toString() = "SagaRun[sagaName=$sagaName, id=$id, parentId=$parentId, attempt=$attempt]"
}
