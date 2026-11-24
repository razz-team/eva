plugins {
    id("eva-kotlin")
    id("eva-publish")
    id("dev.monosoul.jooq-docker")
}

dependencies {
    api(libs.db.scheduler)
    api(libs.kafka.clients)

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.logging)
    implementation(project(eva.eva_serialization))
    implementation(project(eva.eva_jooq))
    implementation(libs.jooq)
    implementation(libs.hikari)
    implementation(libs.postgres)
    implementation(libs.flyway)
    implementation(libs.flyway.postgres)

    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(project(eva.eva_test))
    testImplementation(project(eva.eva_migrations))
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.jackson.kotlin)

    jooqCodegen(platform(libs.jooq.bom))
    jooqCodegen(libs.jooq.codegen)
    jooqCodegen(libs.postgres)
    jooqCodegen(libs.flyway)
    jooqCodegen(libs.flyway.postgres)
}

val eventsMigrations: Configuration by configurations.creating

dependencies {
    eventsMigrations(project(eva.eva_events_db_schema)) {
        isTransitive = false
    }
}

jooq {
    withContainer {
        image {
            name = "postgres:16-alpine"
        }
    }
}

tasks.generateJooqClasses {
    schemas.set(listOf("events"))
    basePackageName.set("com.razz.eva.outbox.db.schema")
    migrationLocations.setFromClasspath(eventsMigrations, "com/razz/eva/events/db")
    usingJavaConfig {
        withName("org.jooq.codegen.KotlinGenerator")
        database.withIncludes("model_events")
        // TIMESTAMP columns hold UTC, as in the rest of eva
        database.withForcedTypes(
            org.jooq.meta.jaxb.ForcedType()
                .withUserType("java.time.Instant")
                .withConverter("com.razz.jooq.converter.InstantConverter")
                .withIncludeTypes("TIMESTAMP"),
        )
        generate.withJavaTimeTypes(false)
        generate.withKotlinNotNullRecordAttributes(true)
    }
}
