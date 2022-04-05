package com.razz.eva.serialization.json

import com.razz.serialization.json.InstantMillisSerializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.*

private fun JsonPrimitive.string(keyName: String) = if (isString) {
    content
} else {
    throw IllegalArgumentException("$keyName should be a string, but it is not")
}

fun JsonObject.jsonObject(key: String) = this.getValue(key).jsonObject

fun JsonObject.jsonObjectOpt(key: String) = this[key]?.takeUnless { it is JsonNull }?.jsonObject

fun JsonObject.jsonArray(key: String) = this.getValue(key).jsonArray

fun JsonObject.jsonArrayOpt(key: String) = this[key]?.takeUnless { it is JsonNull }?.jsonArray

fun JsonObject.string(key: String) = this.getValue(key).jsonPrimitive.string(key)

fun JsonObject.stringOpt(key: String) = this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content

fun JsonObject.int(key: String) = this.getValue(key).jsonPrimitive.int

fun JsonObject.intOpt(key: String) = this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.int

fun JsonObject.long(key: String) = this.getValue(key).jsonPrimitive.long

fun JsonObject.longOpt(key: String) = this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.long

fun JsonObject.double(key: String) = this.getValue(key).jsonPrimitive.double

fun JsonObject.doubleOpt(key: String) = this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.double

fun JsonObject.float(key: String) = this.getValue(key).jsonPrimitive.float

fun JsonObject.floatOpt(key: String) = this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.float

fun JsonObject.uri(key: String) = URI(this.string(key))

fun JsonObject.uriOpt(key: String) = this.stringOpt(key)?.let { URI(it) }

fun JsonObject.uuid(key: String): UUID = UUID.fromString(this.string(key))

fun JsonObject.uuidOpt(key: String) = this.stringOpt(key)?.let { UUID.fromString(it) }

fun JsonObject.instant(key: String): Instant = InstantMillisSerializer.parseFromString(this.string(key))

fun JsonObject.instantOpt(key: String) = this.stringOpt(key)?.let { InstantMillisSerializer.parseFromString(it) }

fun JsonObject.duration(key: String): Duration = Duration.parse(this.string(key))

fun JsonObject.durationOpt(key: String) = this.stringOpt(key)?.let { Duration.parse(it) }

fun JsonObject.boolean(key: String) = this.getValue(key).jsonPrimitive.boolean

fun JsonObject.booleanOpt(key: String) = this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.boolean
