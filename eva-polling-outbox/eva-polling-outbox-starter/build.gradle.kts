plugins {
    id("eva-kotlin")
    application
}

dependencies {
    implementation(project(eva.eva_polling_outbox.eva_polling_outbox_lib))
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.logging)
    implementation(libs.hikari)
    implementation(libs.kaml)

    runtimeOnly(libs.logback)

    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(project(eva.eva_test))
    testImplementation(project(eva.eva_migrations))
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.jooq)
}

application {
    mainClass.set("com.razz.eva.outbox.app.OutboxApplicationKt")
    applicationName = "eva-polling-outbox"
}
