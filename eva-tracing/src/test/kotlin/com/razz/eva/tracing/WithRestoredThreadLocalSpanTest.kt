package com.razz.eva.tracing

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.opentracing.util.ThreadLocalScopeManager
import kotlinx.coroutines.withContext

class WithRestoredThreadLocalSpanTest : ShouldSpec({

    val tracer = Tracing.tracer("test")

    should("should restore the thread local span") {
        val span = tracer.buildSpan("test").start()

        withContext(ActiveSpanElement(span)) {
            withClue("Before restoring thread local should be null") {
                tracer.activeSpan() shouldBe null
            }

            tracer.withRestoredThreadLocalSpan {
                withClue("Inside block should have the span in thread local") {
                    tracer.activeSpan() shouldBe span
                }
            }

            withClue("After block execution thread local should be null") {
                tracer.activeSpan() shouldBe null
            }
        }
    }

    should("clean up thread local scope after the execution, if nothing to restore") {
        val tlsScopeField = ThreadLocalScopeManager::class.java.getDeclaredField("tlsScope").apply {
            isAccessible = true
        }

        fun tlsScope() = tlsScopeField.get(tracer.scopeManager()) as ThreadLocal<*>

        val span = tracer.buildSpan("test").start()

        withContext(ActiveSpanElement(span)) {

            withClue("Before restoring thread local should be null") {
                tlsScope().get() shouldBe null
            }

            tracer.withRestoredThreadLocalSpan {
                withClue("Inside block should have the span in thread local") {
                    tlsScope().get() shouldNotBe null
                }
            }

            withClue("After block execution thread local should be null") {
                tlsScope().get() shouldBe null
            }
        }
    }
})
