package com.razz.eva.saga

import com.razz.eva.domain.Principal
import com.razz.eva.saga.Saga.SagaHaltException
import com.razz.eva.saga.TestSaga.Intermediary.Step0
import com.razz.eva.saga.TestSaga.Intermediary.Step1
import com.razz.eva.saga.TestSaga.Terminal.Finish0
import com.razz.eva.saga.TestSaga.Terminal.Finish1
import com.razz.eva.saga.TestSaga.TestPrincipal
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

internal class SagaSpec : ShouldSpec({

    val principal = TestPrincipal(Principal.Id("cool-id"))

    should("stop after resume reached terminal state") {
        val params = TestSaga.Params({ Finish0("it's time to stop") })
        val state = TestSaga.resume(principal, params)
        state shouldBe Finish0("it's time to stop")
    }

    should("throw SagaHaltException on duplicate state") {
        val params = TestSaga.Params({ Step0("What does the \"B\" Stand for in \"Benoit B. Mandelbrot\"?") })
        val attempt = suspend { TestSaga.resume(principal, params) }
        shouldThrow<SagaHaltException> { attempt() }
    }

    should("return state from onException") {
        val params = TestSaga.Params(
            { throw IllegalArgumentException("can't touch this") },
            { _, _, _, _ -> Finish1("swallowed") },
        )
        val state = TestSaga.resume(principal, params)
        state shouldBe Finish1("swallowed")
    }

    should("pass exception, principal, params and null state to onException when exception was thrown in init") {
        var observedE: Exception? = null
        var observedPrincipal: TestPrincipal? = null
        var observedParams: TestSaga.Params? = null
        var observedCurrentStep: TestSaga.Intermediary? = null

        val params = TestSaga.Params(
            { throw IllegalArgumentException("can't touch this") },
            { e, principal, params, currentStep ->
                observedE = e
                observedPrincipal = principal
                observedParams = params
                observedCurrentStep = currentStep
                Finish1("swallowed")
            },
        )
        val state = TestSaga.resume(principal, params)
        observedE.shouldBeInstanceOf<IllegalArgumentException>()
        observedE?.message shouldBe "can't touch this"
        observedPrincipal shouldBe principal
        observedParams shouldBe params
        observedCurrentStep shouldBe null
        state shouldBe Finish1("swallowed")
    }

    should("pass exception, principal, params and current step to onException when exception was thrown in next") {
        var observedE: Exception? = null
        var observedPrincipal: TestPrincipal? = null
        var observedParams: TestSaga.Params? = null
        var observedCurrentStep: TestSaga.Intermediary? = null

        val params = TestSaga.Params(
            { step ->
                when (step) {
                    is Step0 -> Step1("go go go!")
                    else -> throw IllegalArgumentException("can't touch this")
                }
            },
            { e, principal, params, currentStep ->
                observedE = e
                observedPrincipal = principal
                observedParams = params
                observedCurrentStep = currentStep
                Finish1("swallowed")
            },
        )
        val state = TestSaga.resume(principal, params)
        observedE.shouldBeInstanceOf<IllegalArgumentException>()
        observedE?.message shouldBe "can't touch this"
        observedPrincipal shouldBe principal
        observedParams shouldBe params
        observedCurrentStep shouldBe Step1("go go go!")
        state shouldBe Finish1("swallowed")
    }

    should("resume from very beginning when onException returned null") {
        var wasThrown = false

        val params = TestSaga.Params(
            { step ->
                when (step) {
                    is Step0 -> Step1("go go go!")
                    else -> if (wasThrown) {
                        Finish0("it's time to stop")
                    } else {
                        wasThrown = true
                        throw IllegalArgumentException("can't touch this")
                    }
                }
            },
            { _, _, _, _ -> null },
        )
        val state = TestSaga.resume(principal, params)
        state shouldBe Finish0("it's time to stop")
    }
})
