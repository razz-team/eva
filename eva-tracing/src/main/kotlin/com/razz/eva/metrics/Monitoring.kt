package com.razz.eva.metrics

import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.logging.LoggingMeterRegistry
import io.micrometer.core.instrument.logging.LoggingRegistryConfig
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.Duration

object Monitoring {

    private const val DEFAULT_STEP_SEC = 3L

    fun loggingRegistry(step: Duration = Duration.ofSeconds(DEFAULT_STEP_SEC)): LoggingMeterRegistry {
        val config = object : LoggingRegistryConfig {
            override fun get(key: String): String? = null

            override fun step() = step
        }
        return LoggingMeterRegistry(config, Clock.SYSTEM)
    }

    fun noopRegistry() = SimpleMeterRegistry()
}
