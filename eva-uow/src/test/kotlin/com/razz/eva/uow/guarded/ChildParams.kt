package com.razz.eva.uow.guarded

import com.razz.eva.uow.params.kotlinx.UowParams
import kotlinx.serialization.Serializable

/** The params of every composed child here: none. */
@Serializable
internal object ChildParams : UowParams<ChildParams> {
    override fun serialization() = serializer()
}
