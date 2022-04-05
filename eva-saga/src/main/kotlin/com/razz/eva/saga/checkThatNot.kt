package com.razz.eva.saga

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
inline fun checkThatNot(value: Boolean, lazyException: () -> Exception) {
    contract {
        returns() implies !value
    }
    if (value) throw lazyException()
}
