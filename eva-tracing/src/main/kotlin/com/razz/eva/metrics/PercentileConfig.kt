package com.razz.eva.metrics

internal data class PercentileConfig(
    val lowPercentile: Double = 0.5,
    val midPercentile: Double = 0.9,
    val highPercentile: Double = 0.95
) {
    fun toArray() = doubleArrayOf(lowPercentile, midPercentile, highPercentile)
}
