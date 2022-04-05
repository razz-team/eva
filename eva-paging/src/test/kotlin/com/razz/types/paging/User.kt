package com.razz.types.paging

import java.time.Instant

data class User(
    val name: String,
    val registeredAt: Instant
)
