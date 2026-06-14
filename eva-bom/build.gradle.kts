plugins {
    `java-platform`
    id("eva-platform-publish")
}

javaPlatform {
    allowDependencies()
}

dependencies {
    api(platform(libs.kotlin.bom))
    api(platform(libs.kotlinx.coroutines.bom))
    api(platform(libs.kotlinx.serialization.bom))
    api(platform(libs.opentelemetry.instrumentation.bom))
    api(platform(libs.opentelemetry.instrumentation.alpha.bom))
    api(platform(libs.vertx.bom))
    api(platform(libs.micrometer.bom))
    api(platform(libs.jooq.bom))
    api(platform(libs.jackson.bom))
    api(platform(libs.testcontainers.bom))
    api(platform(libs.kotest.bom))

    constraints {
        // eva modules
        api("team.razz.eva:eva-domain:$version")
        api("team.razz.eva:eva-eventbus:$version")
        api("team.razz.eva:eva-events:$version")
        api("team.razz.eva:eva-events-db-schema:$version")
        api("team.razz.eva:eva-idempotency-key:$version")
        api("team.razz.eva:eva-jooq:$version")
        api("team.razz.eva:eva-migrations:$version")
        api("team.razz.eva:eva-paging:$version")
        api("team.razz.eva:eva-persistence:$version")
        api("team.razz.eva:eva-persistence-jdbc:$version")
        api("team.razz.eva:eva-persistence-vertx:$version")
        api("team.razz.eva:eva-repository:$version")
        api("team.razz.eva:eva-saga:$version")
        api("team.razz.eva:eva-serialization:$version")
        api("team.razz.eva:eva-test:$version")
        api("team.razz.eva:eva-tracing:$version")
        api("team.razz.eva:eva-uow:$version")
        api("team.razz.eva:eva-uow-params-jackson:$version")
        api("team.razz.eva:eva-uow-params-kotlinx:$version")
        api("team.razz.eva:eva-uow-params-kotlinx-compiler:$version")

        // no BOM available
        api(libs.mockk)
    }
}
