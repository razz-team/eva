package com.razz.eva.uow

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelId

internal interface ModelPersisting {

    fun <ID : ModelId<out Comparable<*>>, M : Model<ID, *>> add(model: M)

    fun <ID : ModelId<out Comparable<*>>, M : Model<ID, *>> update(model: M)
}
