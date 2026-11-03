package com.razz.eva.outbox

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant

class OutboxRunnerSpec : BehaviorSpec({

    val t0 = Instant.parse("2026-10-20T12:00:00Z")
    val leeway = Duration.ofSeconds(10)
    val config = OutboxConfig(batchSize = 2, maxIterations = 2, leeway = leeway)

    Given("No unpublished events") {
        val store = FakeOutboxStore()
        val runner = OutboxRunner(store, FakeEventSender(), config)

        When("Runner runs") {
            runner.run()

            Then("It returns after a single unfiltered fetch") {
                store.fetches shouldContainExactly listOf(Instant.EPOCH)
            }
        }
    }

    Given("More unpublished events than fit into the iteration limit") {
        val events = (0L until 5L).map { outboxEvent(insertedAt = t0.plusSeconds(it)) }
        val store = FakeOutboxStore().apply { add(*events.toTypedArray()) }
        val sender = FakeEventSender()
        val runner = OutboxRunner(store, sender, config)

        When("Runner runs") {
            runner.run()

            Then("It stops at the iteration limit") {
                sender.sent shouldContainExactly events.take(4)
                store.published() shouldBe events.take(4).map { it.id }.toSet()
            }
            Then("First fetch is unfiltered, second starts at the latest inserted_at minus leeway") {
                store.fetches shouldContainExactly listOf(Instant.EPOCH, events[1].insertedAt - leeway)
            }
        }

        When("Runner runs again") {
            runner.run()

            Then("It publishes the rest") {
                sender.sent shouldContainExactly events
            }
        }
    }

    Given("Stragglers committed after the first fetch") {
        val first = outboxEvent(insertedAt = t0.plusSeconds(60))
        val second = outboxEvent(insertedAt = t0.plusSeconds(60).plusSeconds(1))
        val withinLeeway = outboxEvent(insertedAt = t0.plusSeconds(60).minusSeconds(5))
        val beyondLeeway = outboxEvent(insertedAt = t0)
        val store = FakeOutboxStore { fetchNumber ->
            if (fetchNumber == 1) add(withinLeeway, beyondLeeway)
        }.apply { add(first, second) }
        val sender = FakeEventSender()
        val runner = OutboxRunner(store, sender, config.copy(maxIterations = 10))

        When("Runner runs") {
            runner.run()

            Then("Straggler within leeway is published in the same run, the other one is not") {
                sender.sent shouldContainExactly listOf(first, second, withinLeeway)
            }
        }

        When("Runner runs again") {
            runner.run()

            Then("Unfiltered first iteration picks up the straggler beyond leeway") {
                sender.sent.last() shouldBe beyondLeeway
            }
        }
    }

    Given("Sender failing in the middle of a batch") {
        val ok = outboxEvent(insertedAt = t0)
        val poison = outboxEvent(insertedAt = t0.plusSeconds(1))
        val store = FakeOutboxStore().apply { add(ok, poison) }
        val runner = OutboxRunner(store, FakeEventSender(failOn = setOf(poison.id)), config)

        When("Runner runs") {
            val ex = shouldThrow<IllegalStateException> { runner.run() }

            Then("Acked prefix is marked as published and the failure is thrown") {
                ex.message shouldBe "broker is down"
                store.published() shouldBe setOf(ok.id)
                store.fetches shouldHaveSize 1
            }
        }
    }
})
