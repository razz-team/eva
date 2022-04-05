package com.razz.eva.serialization.json

import com.razz.serialization.json.InstantMillisSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

object JsonFormat {
    val json = Json {
        serializersModule = SerializersModule {
            contextual(UriSerializer)
            contextual(UuidSerializer)
            contextual(LocaleSerializer)
            contextual(ZoneIdSerializer)
            contextual(InstantMillisSerializer)
            contextual(LocalDateSerializer)
            contextual(LocalDateTimeSecondsSerializer)
            contextual(URLSerializer)
            contextual(DurationSerializer)
            contextual(YearMonthSerializer)
            contextual(BigDecimalSerializer)
        }
        allowStructuredMapKeys = true
        ignoreUnknownKeys = true
    }
}

fun Map<*, *>.toJsonObject(): JsonObject = JsonObject(
    map {
        it.key.toString() to it.value.toJsonElement()
    }.toMap()
)

fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is Number -> this.toJsonPrimitive()
    is String -> this.toJsonPrimitive()
    is Boolean -> this.toJsonPrimitive()
    is Instant -> this.toJsonPrimitive()
    is LocalDate -> this.toJsonPrimitive()
    is LocalDateTime -> this.toJsonPrimitive()
    is Map<*, *> -> this.toJsonObject()
    is Iterable<*> -> JsonArray(this.map { it.toJsonElement() })
    is Array<*> -> JsonArray(this.map { it.toJsonElement() })
    else -> JsonPrimitive(this.toString())
}

fun Number.toJsonPrimitive() = JsonPrimitive(this)
fun String.toJsonPrimitive() = JsonPrimitive(this)
fun Boolean.toJsonPrimitive() = JsonPrimitive(this)
fun Instant.toJsonPrimitive() = JsonPrimitive(InstantMillisSerializer.formatToString(this))
fun LocalDate.toJsonPrimitive() = JsonPrimitive(LocalDateSerializer.formatToString(this))
fun LocalDateTime.toJsonPrimitive() = JsonPrimitive(LocalDateTimeSecondsSerializer.formatToString(this))
