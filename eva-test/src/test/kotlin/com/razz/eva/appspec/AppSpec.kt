package com.razz.eva.appspec

import com.razz.eva.test.WitnessedBody
import com.razz.eva.test.runWitnessed
import io.kotest.core.spec.style.ShouldSpec

// An app's own spec base: it supplies the witness through eva-test's helper alone.
abstract class AppSpec(
    body: WitnessedBody<AppSpec>,
) : ShouldSpec() {

    val appName = "probe-app"

    init {
        runWitnessed(body)
    }
}
