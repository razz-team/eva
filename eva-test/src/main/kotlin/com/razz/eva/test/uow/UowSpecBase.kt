package com.razz.eva.test.uow

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelId
import com.razz.eva.uow.ModelParam
import com.razz.eva.uow.ModelParam.Factory.constantModelParam

interface UowSpecBase {

    fun <MID : ModelId<out Comparable<*>>, M : Model<MID, *>> constantModelParam(
        model: M,
    ): ModelParam<MID, M> {
        return com.razz.eva.uow.TestConstantModelParam.constantModelParam(model)
    }
}
