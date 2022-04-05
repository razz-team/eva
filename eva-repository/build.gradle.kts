plugins {
    id("eva-kotlin")
    id("eva-publish")
}

dependencies {
    api(libs.jooq)

    implementation(libs.vertx_pg)
    implementation(libs.kotlin_coroutines)
    implementation(libs.kotlin_reflect)

    api(project(eva.eva_domain))
    api(project(eva.eva_events))
    api(project(eva.eva_jooq))
    api(project(eva.eva_idempotency_key))
    api(project(eva.eva_tracing))

    implementation(project(eva.eva_paging))
    implementation(project(eva.eva_serialization))
    implementation(project(eva.eva_persistence))
    implementation(project(eva.eva_events_db_schema))

    testImplementation(testFixtures(project(eva.eva_domain)))
    testImplementation(testFixtures(project(eva.eva_test_db_schema)))

    testFixturesImplementation(libs.kotlin_coroutines)
    testFixturesImplementation(project(eva.eva_events))
    testFixturesImplementation(project(eva.eva_events_db_schema))
    testFixturesImplementation(project(eva.eva_test_db_schema))
    testFixturesImplementation(project(eva.eva_serialization))
    testFixturesImplementation(testFixtures(project(eva.eva_domain)))
    testFixturesImplementation(testFixtures(project(eva.eva_persistence)))
}
