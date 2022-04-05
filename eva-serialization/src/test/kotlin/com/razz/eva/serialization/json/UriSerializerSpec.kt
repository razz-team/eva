package com.razz.eva.serialization.json

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.net.URI

class UriSerializerSpec : BehaviorSpec({

    Given("Image has uri address") {
        val image = Image(URI("http://razzbank.com/icon"))

        lateinit var imageJson: String

        When("Principal encodes image to json") {
            imageJson = JsonFormat.json.encodeToString(image)

            Then("Correct json will be created") {
                imageJson shouldBe """{"uri":"http://razzbank.com/icon"}"""
            }
        }

        When("Principal decodes image") {
            val decodedImage = JsonFormat.json.decodeFromString<Image>(imageJson)

            Then("Original image should be returned") {
                decodedImage shouldBe image
            }
        }
    }
})
