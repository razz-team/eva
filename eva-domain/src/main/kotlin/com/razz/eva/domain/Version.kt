package com.razz.eva.domain

import com.razz.eva.domain.Version.Companion.version

@JvmInline
value class Version private constructor(
    val version: Long
) {
    init {
        check(version >= 0) {
            "version should be positive"
        }
    }

    companion object {
        val V0 = Version(0L)
        val V1 = Version(1L)

        fun version(version: Long): Version {
            return when (version) {
                V0.version -> V0
                else -> Version(version)
            }
        }
    }
}

interface Versioned {
    fun version(): Version
}

fun Long.toVersion() = version(this)
