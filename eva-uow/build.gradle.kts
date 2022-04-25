plugins {
    id("eva-kotlin")
    id("eva-publish")
}

kotlin.target.compilations.getByName("testFixtures") {
    associateWith(kotlin.target.compilations.getByName("main"))
}

dependencies {
    implementation(libs.kotlin_stdlib)
    implementation(libs.kotlin_reflect)
    implementation(libs.kotlin_coroutines)
    implementation(libs.kotlin_logging)

    api(project(eva.eva_idempotency_key))
    api(project(eva.eva_domain))
    api(project(eva.eva_events))
    api(project(eva.eva_tracing))
    api(project(eva.eva_persistence))
    implementation(project(eva.eva_repository))

    testImplementation(project(eva.eva_migrations))
    testImplementation(project(eva.eva_persistence))
    testImplementation(project(eva.eva_persistence_jdbc))
    testImplementation(project(eva.eva_persistence_vertx))
    testImplementation(project(eva.eva_test))
    testImplementation(project(eva.eva_serialization))
    testImplementation(testFixtures(project(eva.eva_domain)))
    testImplementation(testFixtures(project(eva.eva_repository)))
    testImplementation(testFixtures(project(eva.eva_persistence)))

    testFixturesImplementation(libs.kotest_framework)
    testFixturesImplementation(libs.kotlin_coroutines)
    testFixturesImplementation(project(eva.eva_repository))
    testFixturesImplementation(project(eva.eva_test))
    testFixturesImplementation(testFixtures(project(eva.eva_repository)))
    testFixturesImplementation(testFixtures(project(eva.eva_domain)))
    testFixturesImplementation(testFixtures(project(eva.eva_persistence)))
}
