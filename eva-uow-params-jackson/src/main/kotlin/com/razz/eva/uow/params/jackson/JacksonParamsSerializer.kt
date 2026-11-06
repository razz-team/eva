package com.razz.eva.uow.params.jackson

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.razz.eva.uow.ParamsSerializer
import com.razz.eva.uow.UowParams

/**
 * Writes params with [objectMapper], a copy of it with [GuardedAuditModule] added: a proof bound into params
 * writes its audit fields, never the proof.
 */
class JacksonParamsSerializer(
    objectMapper: ObjectMapper = jacksonObjectMapper(),
) : ParamsSerializer {

    private val objectMapper: ObjectMapper = objectMapper.copy().registerModule(GuardedAuditModule)

    override fun <PARAMS : UowParams<PARAMS>> serialize(params: PARAMS): String {
        return objectMapper.writeValueAsString(params)
    }
}
