import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("eva-kotlin")
    id("eva-publish")
}

tasks.withType<KotlinCompile>().configureEach {
    friendPaths.from(
        rootProject.project("eva-domain").layout.buildDirectory.dir("classes/kotlin/main"),
    )
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
    api(project(eva.eva_persistence))

    implementation(project(eva.eva_paging))
    implementation(project(eva.eva_serialization))
    implementation(project(eva.eva_events_db_schema))

    testImplementation(project(eva.eva_uow))
    testImplementation(project(eva.eva_test))
    testImplementation(testFixtures(project(eva.eva_domain)))
    testImplementation(testFixtures(project(eva.eva_repository)))

    testFixturesImplementation(libs.kotlin_coroutines)
    testFixturesImplementation(project(eva.eva_events))
    testFixturesImplementation(project(eva.eva_events_db_schema))
    testFixturesImplementation(project(eva.eva_repository))
    testFixturesImplementation(project(eva.eva_serialization))
    testFixturesImplementation(testFixtures(project(eva.eva_domain)))
    testFixturesImplementation(testFixtures(project(eva.eva_persistence)))
}
