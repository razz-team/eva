object versions {
    val java = org.gradle.api.JavaVersion.VERSION_17
    val jvm = "17"
    val kotlin = "1.6.10"
    val kotlin_coroutines = "1.6.0"
    val micrometer = "1.8.4"
    val detekt = "1.18.1"
    val shadow_jar_plugin = "7.0.0"
    val logback_json = "0.1.5"
    val log4j = "2.16.0"
    val jackson = "2.13.2"
    val mockk = "1.12.3"
    val testcontainers = "1.16.3"
    val postgresql = "42.3.3"
    val vertx = "4.2.6"
    val kafka_clients = "3.1.0"
}

object libs {
    val kotlin_serialization = "org.jetbrains.kotlin:kotlin-serialization:${versions.kotlin}"
    val kotlinx_coroutines_bom = "org.jetbrains.kotlinx:kotlinx-coroutines-bom:${versions.kotlin_coroutines}"
    val kotlin_bom = "org.jetbrains.kotlin:kotlin-bom:${versions.kotlin}"
    val jackson_bom = "com.fasterxml.jackson:jackson-bom:${versions.jackson}"
    val logback_jackson = "ch.qos.logback.contrib:logback-jackson:${versions.logback_json}"
    val log4j = "org.apache.logging.log4j:log4j-core:${versions.log4j}"
    val postgres = "org.postgresql:postgresql:${versions.postgresql}"
    val vertx_kotlin = "io.vertx:vertx-lang-kotlin:${versions.vertx}"
    val mockk = "io.mockk:mockk:${versions.mockk}"
    val kafka_clients = "org.apache.kafka:kafka-clients:${versions.kafka_clients}"
    val testcontainers_bom = "org.testcontainers:testcontainers-bom:${versions.testcontainers}"
    val detekt = "io.gitlab.arturbosch.detekt:detekt-formatting:${versions.detekt}"
    val micrometer_prometheus = "io.micrometer:micrometer-registry-prometheus:${versions.micrometer}"

    val detekt_plugin = "io.gitlab.arturbosch.detekt:detekt-gradle-plugin:${versions.detekt}"
    val shadow_jar_plugin = "gradle.plugin.com.github.jengelman.gradle.plugins:shadow:${versions.shadow_jar_plugin}"
    val kotlin_plugin = "org.jetbrains.kotlin:kotlin-gradle-plugin:${versions.kotlin}"
}
