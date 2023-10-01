package com.razz.eva.test.uow

import com.razz.eva.uow.Clocks.fixedUTC
import com.razz.eva.uow.Clocks.millisUTC
import com.razz.eva.uow.verify.EqualityVerifier
import com.razz.eva.uow.verify.EqualityVerifierAware
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

abstract class UowBehaviorSpec(
    body: UowBehaviorSpec.() -> Unit = {}
) : BehaviorSpec(), EqualityVerifierAware {

    val now = millisUTC().instant()
    val clock = fixedUTC(now)

    private object KotestEqualityVerifier : EqualityVerifier {
        override fun <T> verify(expected: T, actual: T) {
            expected shouldBe actual
        }
    }

    override val equalityVerifier: EqualityVerifier = KotestEqualityVerifier

    init {
        this.body()
    }
}
