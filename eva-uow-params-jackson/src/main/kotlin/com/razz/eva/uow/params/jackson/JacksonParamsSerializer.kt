package com.razz.eva.uow.params.jackson

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.razz.eva.uow.ParamsSerializer
import com.razz.eva.uow.UowParams

/**
 * Writes params with a copy of [objectMapper] that has [GuardedAuditModule] registered, so a
 * [com.razz.eva.uow.guarded.Proven] field is written as its action and model id. The mapper passed in is not changed.
 */
class JacksonParamsSerializer(
    objectMapper: ObjectMapper = jacksonObjectMapper(),
) : ParamsSerializer {

    private val objectMapper: ObjectMapper = objectMapper.copy().registerModule(GuardedAuditModule)

    override fun <PARAMS : UowParams<PARAMS>> serialize(params: PARAMS): String {
        return objectMapper.writeValueAsString(params)
    }
}
