object versions {
    val java = org.gradle.api.JavaVersion.VERSION_21
    val jvm = "21"
    val kotlin = "1.9.24"
    val kotlin_coroutines = "1.8.1"
    val kotlin_serialization_json = "1.6.3"
    val kotest = "5.9.0"
    val jooq = "3.19.8"
    val flywaydb = "10.13.0"
    val opentracing = "0.33.0"
    val opentelemetry = "1.40.0"
    val jaeger = "1.8.1"
    val micrometer = "1.13.0"
    val detekt = "1.23.6"
    val logback = "1.5.6"
    val kotlin_logging = "3.0.5"
    val kotlin_slf4 = "1.8.1"
    val jackson = "2.17.1"
    val mockk = "1.13.11"
    val testcontainers = "1.19.8"
    val postgresql = "42.7.3"
    val vertx = "4.5.8"
    val ongres_scram = "2.1"
    val hikari = "5.1.0"
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
    val kotest_runner = "io.kotest:kotest-runner-junit5-jvm:${versions.kotest}"
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
    val opentelemetry_sdk = "io.opentelemetry:opentelemetry-sdk:${versions.opentelemetry}"
    val opentelemetry_kotlin = "io.opentelemetry:opentelemetry-extension-kotlin:${versions.opentelemetry}"
    val flyway = "org.flywaydb:flyway-core:${versions.flywaydb}"
    val flyway_postgres = "org.flywaydb:flyway-database-postgresql:${versions.flywaydb}"
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
