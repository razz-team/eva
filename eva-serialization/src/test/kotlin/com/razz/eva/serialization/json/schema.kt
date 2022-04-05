package com.razz.eva.serialization.json

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI
import java.time.ZoneId
import java.util.*

@Serializable
data class User(
    val id: @Contextual UUID
)

@Serializable
data class Image(
    val uri: @Contextual URI
)

@Serializable
data class Template(
    val locale: @Contextual Locale
)

@Serializable
data class Agent(
    val timezone: @Contextual ZoneId
)

@Serializable
sealed class TestError {

    @Serializable
    @SerialName("UserNotFound")
    data class UserNotFound(
        val user: User
    ) : TestError()

    @Serializable
    @SerialName("Snafu")
    object Snafu : TestError()
}
