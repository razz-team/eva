package com.razz.eva.paging

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class Size(private val value: Int) {
    companion object {
        val DEFAULT = Size(1000)
    }

    init {
        require(value > 0) {
            "Limit must be positive, but was - $value"
        }
    }

    fun intValue() = value

    fun maxSize(size: Size) = Size(Integer.max(value, size.value))

    fun minSize(size: Size) = Size(Integer.min(value, size.value))
}
