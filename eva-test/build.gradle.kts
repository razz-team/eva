plugins {
    id("eva-kotlin")
    id("eva-publish")
}

dependencies {
    implementation(project(eva.eva_domain))
    implementation(project(eva.eva_repository))
    implementation(project(eva.eva_uow))
    implementation(project(eva.eva_saga))
    implementation(project(eva.eva_migrations))
    implementation(project(eva.eva_persistence_jdbc))
    implementation(project(eva.eva_persistence_vertx))
    implementation(testFixtures(project(eva.eva_uow)))

    implementation(platform(libs.testcontainers_bom))

    implementation(libs.kotlin_reflect)
    implementation(libs.kotest_framework)
    implementation(libs.postgres)
    implementation(libs.kotest_runner)
    implementation(libs.mockk)
    implementation(libs.hikari)
    implementation(libs.testcontainers)
    implementation(libs.testcontainers_postgres)
    implementation(libs.kotlin_logging)
    implementation(libs.flyway)
    implementation(libs.flyway_postgres)
    implementation(libs.opentelemetry_sdk_testing)

    testImplementation(testFixtures(project(eva.eva_repository)))
    testImplementation(testFixtures(project(eva.eva_domain)))
}
