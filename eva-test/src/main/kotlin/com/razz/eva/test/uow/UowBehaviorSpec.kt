package com.razz.eva.test.uow

import com.razz.eva.uow.Clocks.fixedUTC
import com.razz.eva.uow.Clocks.millisUTC
import com.razz.eva.uow.ServicePrincipal
import io.kotest.core.spec.style.BehaviorSpec

abstract class UowBehaviorSpec(
    body: UowBehaviorSpec.() -> Unit = {}
) : BehaviorSpec() {

    val now = millisUTC().instant()
    val clock = fixedUTC(now)
    val principal = ServicePrincipal.byName("Kotest")

    init {
        this.body()
    }
}
