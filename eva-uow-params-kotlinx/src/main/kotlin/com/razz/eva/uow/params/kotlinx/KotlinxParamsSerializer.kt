package com.razz.eva.uow.params.kotlinx

import com.razz.eva.uow.ParamsSerializer
import com.razz.eva.uow.guarded.Guarded
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.StringFormat

class KotlinxParamsSerializer(
    private val json: StringFormat = com.razz.eva.serialization.json.JsonFormat.json,
) : ParamsSerializer {
    override fun <PARAMS : com.razz.eva.uow.UowParams<PARAMS>> serialize(params: PARAMS): String = when (params) {
        is Guarded<*, *, *> -> json.encodeToString(GuardedAudit(strategyOf(params.params)), params)
        else -> json.encodeToString(strategyOf(params), params)
    }

    // Every params type is a kotlinx one at run time; the base interface the executor types on cannot say so.
    @Suppress("UNCHECKED_CAST")
    private fun strategyOf(params: com.razz.eva.uow.UowParams<*>): SerializationStrategy<Any?> {
        val kotlinxParams = params as? UowParams<*>
            ?: error("${params::class.simpleName} must implement ${UowParams::class.simpleName}")
        return kotlinxParams.serialization() as SerializationStrategy<Any?>
    }
}
