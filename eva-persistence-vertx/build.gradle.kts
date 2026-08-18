import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("eva-kotlin")
    id("eva-publish")
}

tasks.withType<KotlinCompile>().configureEach {
    friendPaths.from(
        rootProject.project("eva-tracing").layout.buildDirectory.dir("classes/kotlin/main"),
    )
}

dependencies {
    api(platform(libs.vertx.bom))
    implementation(platform(libs.vertx.bom))

    api(libs.vertx.pg)

    api(project(eva.eva_persistence))
    api(project(eva.eva_tracing))

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.coroutines)

    implementation(libs.vertx.kotlin)
    implementation(libs.vertx.kotlin.coroutines)
    implementation(libs.jooq)
    implementation(libs.jooq.postgres)

    testImplementation(testFixtures(project(eva.eva_persistence)))
    testImplementation(libs.opentelemetry.sdk.testing)
}
