package com.razz.eva.uow

import com.razz.eva.domain.ModelId
import com.razz.eva.persistence.PersistenceException.ConnectionException
import com.razz.eva.persistence.PersistenceException.ModelRecordConstraintViolationException
import com.razz.eva.persistence.PersistenceException.StaleRecordException
import com.razz.eva.persistence.PersistenceException.UniqueModelRecordViolationException
import com.razz.eva.uow.Retry.ConnectionBackoffRetry
import com.razz.eva.uow.Retry.ConnectionFixedRetry
import com.razz.eva.uow.Retry.StaleRecordFixedRetry
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Duration
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

    Given("ConnectionFixedRetry with 1 attempt") {
        val retry = ConnectionFixedRetry(1, ofMillis(50))

        When("Retry is polled for next delay for zeroth attempt and ConnectionException") {
            val nextDelay = retry.getNextDelay(0, ConnectionException(RuntimeException("pool exhausted")))

            Then("Next delay should be 50 millis") {
                nextDelay shouldBe ofMillis(50)
            }
        }

        When("Retry is polled for next delay for first attempt and ConnectionException") {
            val nextDelay = retry.getNextDelay(1, ConnectionException(RuntimeException("pool exhausted")))

            Then("Next delay should be null") {
                nextDelay shouldBe null
            }
        }

        When("Retry is polled for next delay for zeroth attempt and StaleRecordException") {
            val nextDelay = retry.getNextDelay(0, StaleRecordException(whateverModelId, "cool_table"))

            Then("Next delay should be null") {
                nextDelay shouldBe null
            }
        }
    }

    Given("ConnectionBackoffRetry with base 100 millis capped at 2 seconds") {
        val retry = ConnectionBackoffRetry(10, ofMillis(100), ofMillis(2_000))

        When("Ceiling is computed per attempt") {
            Then("Ceiling doubles per attempt until the cap") {
                retry.ceiling(0) shouldBe ofMillis(100)
                retry.ceiling(1) shouldBe ofMillis(200)
                retry.ceiling(4) shouldBe ofMillis(1_600)
                retry.ceiling(5) shouldBe ofMillis(2_000)
                retry.ceiling(50) shouldBe ofMillis(2_000)
            }
        }

        When("Retry is polled for next delay for second attempt and ConnectionException") {
            val nextDelay = retry.getNextDelay(2, ConnectionException(RuntimeException("connection reset")))

            Then("Next delay should be jittered within the attempt ceiling") {
                nextDelay shouldNotBe null
                (nextDelay!! >= Duration.ZERO) shouldBe true
                (nextDelay <= ofMillis(400)) shouldBe true
            }
        }

        When("Retry is polled for next delay for tenth attempt and ConnectionException") {
            val nextDelay = retry.getNextDelay(10, ConnectionException(RuntimeException("connection reset")))

            Then("Next delay should be null") {
                nextDelay shouldBe null
            }
        }

        When("Retry is polled for next delay for zeroth attempt and StaleRecordException") {
            val nextDelay = retry.getNextDelay(0, StaleRecordException(whateverModelId, "cool_table"))

            Then("Next delay should be null") {
                nextDelay shouldBe null
            }
        }
    }
})
