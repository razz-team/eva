package com.razz.eva.domain

import com.razz.eva.domain.Principal.Id
import com.razz.eva.domain.Principal.Name
import com.razz.eva.domain.TestModelEvent.TestModelEventWithOverridePrincipal
import com.razz.eva.domain.TestModelEvent.TestModelEventWithPrincipal
import com.razz.eva.domain.TestModelId.Companion.randomTestModelId
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ModelEventSpec : ShouldSpec({

    should("Common model event payload") {
        val event = TestModelEvent.TestModelEvent1(randomTestModelId())

        event.payload(TestPrincipal) shouldBe buildJsonObject { }
        event.integrationEvent() shouldBe buildJsonObject { }
    }

    should("Model event payload with principal") {
        val event = TestModelEventWithPrincipal(randomTestModelId())

        event.payload(TestPrincipal) shouldBe buildJsonObject {
            put("principalId", "THIS_IS_SINGLETON")
            put("principalName", "TEST_PRINCIPAL")
        }
        event.integrationEvent() shouldBe buildJsonObject { }
    }

    should("Model event payload with override principal") {
        val anotherTestPrincipal = object : Principal<String> {
            override val id = Id("ANOTHER")
            override val name = Name("ANOTHER")
        }
        val event = TestModelEventWithOverridePrincipal(randomTestModelId(), anotherTestPrincipal)

        event.payload(TestPrincipal) shouldBe buildJsonObject {
            put("principalId", "ANOTHER")
            put("principalName", "TEST_PRINCIPAL")
        }
        event.integrationEvent() shouldBe buildJsonObject {
            put("principalId", "ANOTHER")
        }
    }
})
