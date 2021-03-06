object versions {
    val java = org.gradle.api.JavaVersion.VERSION_17
    val jvm = "17"
    val kotlin = "1.7.10"
    val kotlin_coroutines = "1.6.4"
    val kotlin_serialization_json = "1.3.3"
    val kotest = "5.3.2"
    val jooq = "3.17.2"
    val flywaydb = "9.0.0"
    val opentracing = "0.33.0"
    val jaeger = "1.8.1"
    val micrometer = "1.9.2"
    val detekt = "1.20.0"
    val logback = "1.2.11"
    val kotlin_logging = "2.1.23"
    val kotlin_slf4 = "1.6.4"
    val jackson = "2.13.3"
    val mockk = "1.12.4"
    val testcontainers = "1.17.3"
    val postgresql = "42.4.0"
    val vertx = "4.3.2"
    val ongres_scram = "2.1"
    val hikari = "5.0.1"
    val kafka_clients = "3.1.0"
}

object libs {
    val kotlin_stdlib = "org.jetbrains.kotlin:kotlin-stdlib"
    val kotlin_reflect = "org.jetbrains.kotlin:kotlin-reflect"
    val kotlin_serialization = "org.jetbrains.kotlin:kotlin-serialization:${versions.kotlin}"
    val kotlin_logging = "io.github.microutils:kotlin-logging:${versions.kotlin_logging}"
    val kotlin_slf4 = "org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:${versions.kotlin_slf4}"
    val kotlinx_coroutines_bom = "org.jetbrains.kotlinx:kotlinx-coroutines-bom:${versions.kotlin_coroutines}"
    val kotlin_bom = "org.jetbrains.kotlin:kotlin-bom:${versions.kotlin}"
    val kotlin_coroutines = "org.jetbrains.kotlinx:kotlinx-coroutines-core:${versions.kotlin_coroutines}"
    val kotlinx_serialization = "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm"
    val kotlinx_serialization_json = "org.jetbrains.kotlinx:kotlinx-serialization-json:${versions.kotlin_serialization_json}"
    val kotest_runner = "io.kotest:kotest-runner-junit5:${versions.kotest}"
    val kotest_assertions_json = "io.kotest:kotest-assertions-json-jvm:${versions.kotest}"
    val kotest_assertions_core = "io.kotest:kotest-assertions-core-jvm:${versions.kotest}"
    val kotest_framework = "io.kotest:kotest-framework-api:${versions.kotest}"
    val hikari = "com.zaxxer:HikariCP:${versions.hikari}"
    val jooq = "org.jooq:jooq:${versions.jooq}"
    val jooq_postgres = "org.jooq:jooq-postgres-extensions:${versions.jooq}"
    val jackson_bom = "com.fasterxml.jackson:jackson-bom:${versions.jackson}"
    val jaeger_client = "io.jaegertracing:jaeger-client:${versions.jaeger}"
    val jaeger_micrometer = "io.jaegertracing:jaeger-micrometer:${versions.jaeger}"
    val logback = "ch.qos.logback:logback-classic:${versions.logback}"
    val micrometer = "io.micrometer:micrometer-core:${versions.micrometer}"
    val opentracing_api = "io.opentracing:opentracing-api:${versions.opentracing}"
    val opentracing_noop = "io.opentracing:opentracing-noop:${versions.opentracing}"
    val flyway = "org.flywaydb:flyway-core:${versions.flywaydb}"
    val postgres = "org.postgresql:postgresql:${versions.postgresql}"
    val vertx_pg = "io.vertx:vertx-pg-client:${versions.vertx}"
    val vertx_kotlin = "io.vertx:vertx-lang-kotlin:${versions.vertx}"
    val vertx_kotlin_coroutines = "io.vertx:vertx-lang-kotlin-coroutines:${versions.vertx}"
    val ongres_scram = "com.ongres.scram:client:${versions.ongres_scram}"
    val mockk = "io.mockk:mockk:${versions.mockk}"
    val kafka_clients = "org.apache.kafka:kafka-clients:${versions.kafka_clients}"
    val testcontainers_bom = "org.testcontainers:testcontainers-bom:${versions.testcontainers}"
    val testcontainers = "org.testcontainers:testcontainers"
    val testcontainers_postgres = "org.testcontainers:postgresql"
    val detekt = "io.gitlab.arturbosch.detekt:detekt-formatting:${versions.detekt}"
    val micrometer_prometheus = "io.micrometer:micrometer-registry-prometheus:${versions.micrometer}"

    val detekt_plugin = "io.gitlab.arturbosch.detekt:detekt-gradle-plugin:${versions.detekt}"
    val kotlin_plugin = "org.jetbrains.kotlin:kotlin-gradle-plugin:${versions.kotlin}"
}
