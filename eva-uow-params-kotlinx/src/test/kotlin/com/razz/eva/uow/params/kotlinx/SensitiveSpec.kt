package com.razz.eva.uow.params.kotlinx

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class SensitiveSpec : ShouldSpec({

    should("serialize a sensitive field as a redaction marker") {
        val params = LoginParams(user = "sergey", password = Sensitive("hunter2"))
        val encoded = Json.encodeToString(LoginParams.serializer(), params)
        encoded shouldBe """{"user":"sergey","password":"***"}"""
    }

    should("keep the wrapped value available in memory") {
        Sensitive("hunter2").value shouldBe "hunter2"
    }

    should("redact toString") {
        Sensitive("hunter2").toString() shouldBe "***"
    }

    should("refuse to deserialize a redacted value") {
        val ex = shouldThrow<IllegalStateException> {
            Json.decodeFromString(LoginParams.serializer(), """{"user":"sergey","password":"***"}""")
        }
        ex.message shouldContain "redacted"
    }
})

@Serializable
private data class LoginParams(
    val user: String,
    val password: Sensitive<String>,
)
