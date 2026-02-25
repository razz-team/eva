package com.razz.eva.uow.params.kotlinx

import com.razz.eva.uow.ParamsSerializer
import kotlinx.serialization.StringFormat

class KotlinxParamsSerializer(
    private val json: StringFormat = com.razz.eva.serialization.json.JsonFormat.json,
) : ParamsSerializer {
    override fun <PARAMS : com.razz.eva.uow.UowParams<PARAMS>> serialize(params: PARAMS): String {
        @Suppress("UNCHECKED_CAST")
        val kotlinxParams = params as? UowParams<PARAMS>
            ?: error("${params::class.simpleName} must implement ${UowParams::class.simpleName}")
        return json.encodeToString(kotlinxParams.serialization(), params)
    }
}
