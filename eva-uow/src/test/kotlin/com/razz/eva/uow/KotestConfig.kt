package com.razz.eva.uow

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.spec.IsolationMode.SingleInstance

object KotestConfig : AbstractProjectConfig() {

    override val parallelism = 2

    override val isolationMode = SingleInstance
}
