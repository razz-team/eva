package com.razz.eva.uow

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelId
import com.razz.eva.uow.ModelParam.Factory.constantModelParam

object TestConstantModelParam {
    fun <MID : ModelId<out Comparable<*>>, M : Model<MID, *>> constantModelParamForSpec(
        model: M,
    ): ModelParam<MID, M> {
        return InstantiationContext.Internal(0).constantModelParam(model)
    }
}
