package com.razz.eva.test.uow

import com.razz.eva.domain.Principal
import com.razz.eva.uow.Clocks.fixedUTC
import com.razz.eva.uow.Clocks.millisUTC
import com.razz.eva.uow.verify.EqualityVerifier
import com.razz.eva.uow.verify.EqualityVerifierAware
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

abstract class UowShouldSpec(
    body: UowShouldSpec.() -> Unit = {},
) : ShouldSpec(), UowSpecBase, EqualityVerifierAware {

    val now = millisUTC().instant()
    val clock = fixedUTC(now)
    val principal: Principal<String> = UowSpecPrincipal

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
