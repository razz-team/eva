package com.razz.eva.uow.params.jackson

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.razz.eva.uow.ParamsSerializer
import com.razz.eva.uow.UowParams

class JacksonParamsSerializer(
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) : ParamsSerializer {
    override fun <PARAMS : UowParams<PARAMS>> serialize(params: PARAMS): String {
        return objectMapper.writeValueAsString(params)
    }
}
