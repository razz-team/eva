package com.razz.eva.serialization.json

import com.razz.eva.paging.Page
import com.razz.eva.paging.Page.First
import com.razz.eva.paging.Page.Next
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.util.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: @Contextual UUID,
)

@Serializable
data class Image(
    val uri: @Contextual URI,
)

@Serializable
data class Template(
    val locale: @Contextual Locale,
)

@Serializable
data class Agent(
    val timezone: @Contextual ZoneId,
)

@Serializable
sealed class TestError {

    @Serializable
    @SerialName("UserNotFound")
    data class UserNotFound(
        val user: User,
    ) : TestError()

    @Serializable
    @SerialName("Snafu")
    object Snafu : TestError()
}

@Serializable
data class Result(
    val pureFirst: @Contextual First<@Contextual Instant>,
    val pureNext: @Contextual Next<@Contextual Instant>,
    val commonNext: @Contextual Page<@Contextual Instant>,
    val commonFirst: @Contextual Page<@Contextual Instant>,
)
