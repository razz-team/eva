package com.razz.eva.test.uow

import com.razz.eva.domain.ModelId
import com.razz.eva.domain.Principal
import com.razz.eva.domain.Witness
import com.razz.eva.domain.mutating
import com.razz.eva.uow.Clocks.fixedUTC
import com.razz.eva.uow.Clocks.millisUTC
import com.razz.eva.uow.verify.EqualityVerifier
import com.razz.eva.uow.verify.EqualityVerifierAware
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.opentelemetry.api.OpenTelemetry

/** The body runs with a witness for every model id type, so a test calls a witnessed mutator directly. */
abstract class UowBehaviorSpec(
    body: context(Witness<ModelId<out Comparable<*>>>) UowBehaviorSpec.() -> Unit = {},
) : BehaviorSpec(), UowSpecBase, EqualityVerifierAware {

    val now = millisUTC().instant()
    val clock = fixedUTC(now)
    val principal: Principal<String> = UowSpecPrincipal
    val executionContext = executionContext(clock, OpenTelemetry.noop())

    private object KotestEqualityVerifier : EqualityVerifier {
        override fun <T> verify(expected: T, actual: T) {
            expected shouldBe actual
        }
    }

    override val equalityVerifier: EqualityVerifier = KotestEqualityVerifier

    init {
        mutating { body() }
    }
}
