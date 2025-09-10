package com.razz.eva.uow

import com.razz.eva.domain.ModelId
import com.razz.eva.persistence.PersistenceException.ModelRecordConstraintViolationException
import com.razz.eva.persistence.PersistenceException.StaleRecordException
import com.razz.eva.persistence.PersistenceException.UniqueModelRecordViolationException
import com.razz.eva.uow.Retry.StaleRecordFixedRetry
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Duration.ofMillis
import java.util.*
import java.util.UUID.randomUUID

class RetrySpec : BehaviorSpec({

    val whateverModelId = object : ModelId<UUID> {
        override val id = randomUUID()
    }

    Given("FixedRetry with 0 attempts") {
        val retry = StaleRecordFixedRetry(0, ofMillis(0))

        When("Retry is polled for next delay for zeroth attempt") {
            val nextDelay = retry.getNextDelay(0, StaleRecordException(whateverModelId, "cool_table"))

            Then("Next delay should be null") {
                nextDelay shouldBe null
            }
        }
    }

    Given("FixedRetry with 1 attempts") {
        val retry = StaleRecordFixedRetry(1, ofMillis(0))

        When("Retry is polled for next delay for zeroth attempt") {
            val nextDelay = retry.getNextDelay(0, StaleRecordException(whateverModelId, "cool_table"))

            Then("Next delay should be 0 millis") {
                nextDelay shouldBe ofMillis(0)
            }
        }

        When("Retry is polled for next delay for zeroth attempt and ModelRecordConstraintViolationException") {
            val nextDelay = retry
                .getNextDelay(0, ModelRecordConstraintViolationException(whateverModelId, "puk", "puk_idx"))

            Then("Next delay should be 0 millis") {
                nextDelay shouldBe null
            }
        }

        When("Retry is polled for next delay for zeroth attempt and UniqueModelRecordViolationException") {
            val nextDelay = retry
                .getNextDelay(0, UniqueModelRecordViolationException(whateverModelId, "puk", "puk_idx"))

            Then("Next delay should be 0 millis") {
                nextDelay shouldBe null
            }
        }
    }
})
