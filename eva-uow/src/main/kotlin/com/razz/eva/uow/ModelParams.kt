package com.razz.eva.uow

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelId

class ModelParams<PARAMS : UowParams<PARAMS>> private constructor(
    paramsFactoryWithReceiver: ModelParams<PARAMS>.() -> PARAMS,
) : () -> PARAMS {

    private val paramsFactory: () -> PARAMS = { with(this) { paramsFactoryWithReceiver() } }

    override fun invoke(): PARAMS {
        val params = paramsFactory()
        firstTime = false
        return params
    }

    private var firstTime = true

    fun <MID: ModelId<*>, M: Model<MID, *>> modelParam(model: M, modelQueries: suspend (MID) -> M): ModelParam<MID, M> {
        val modelParam = if (firstTime) {
            ModelParam(model, modelQueries)
        } else {
            ModelParam(model.id(), modelQueries)
        }
        return modelParam
    }

    companion object Factory {
        operator fun <PARAMS : UowParams<PARAMS>> invoke(
            paramsFactoryWithReceiver: ModelParams<PARAMS>.() -> PARAMS,
        ): ModelParams<PARAMS> {
            return ModelParams(paramsFactoryWithReceiver)
        }
    }
}
