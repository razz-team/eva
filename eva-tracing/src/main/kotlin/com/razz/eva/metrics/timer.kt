package com.razz.eva.metrics

import io.micrometer.core.instrument.Timer

fun timerBuilder(name: String): Timer.Builder = Timer.builder(name)
    .publishPercentiles(*PercentileConfig().toArray())
