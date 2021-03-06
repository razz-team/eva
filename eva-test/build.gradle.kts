plugins {
    id("eva-kotlin")
    id("eva-publish")
}

dependencies {
    implementation(project(eva.eva_domain))
    implementation(project(eva.eva_repository))
    implementation(project(eva.eva_uow))
    implementation(project(eva.eva_migrations))
    implementation(project(eva.eva_persistence_jdbc))
    implementation(project(eva.eva_persistence_vertx))

    implementation(platform(libs.testcontainers_bom))

    implementation(libs.kotest_framework)
    implementation(libs.postgres)
    implementation(libs.kotest_runner)
    implementation(libs.hikari)
    implementation(libs.testcontainers)
    implementation(libs.testcontainers_postgres)
    implementation(libs.kotlin_logging)
    implementation(libs.flyway)

    testImplementation(testFixtures(project(eva.eva_repository)))
    testImplementation(testFixtures(project(eva.eva_domain)))
}
