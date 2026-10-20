package com.razz.eva.outbox

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.time.Duration

class CappedExponentialBackoffSpec : BehaviorSpec({

    Given("Backoff from 1 second doubling up to 1 minute") {
        val backoff = CappedExponentialBackoff<Void>(Duration.ofSeconds(1), Duration.ofMinutes(1))

        Then("Delay doubles with every consecutive failure") {
            (0..5).map { backoff.calculateDelay(it).toSeconds() } shouldContainExactly listOf(1L, 2L, 4L, 8L, 16L, 32L)
        }
        Then("Delay is capped") {
            backoff.calculateDelay(6) shouldBe Duration.ofMinutes(1)
            backoff.calculateDelay(Int.MAX_VALUE) shouldBe Duration.ofMinutes(1)
        }
    }

    Given("Max shorter than initial") {
        Then("Backoff is rejected") {
            shouldThrow<IllegalArgumentException> {
                CappedExponentialBackoff<Void>(Duration.ofMinutes(1), Duration.ofSeconds(1))
            }
        }
    }
})
