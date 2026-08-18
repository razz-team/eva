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
    api(libs.hikari)
    api(libs.kotlin.coroutines)

    api(project(eva.eva_persistence))
    api(project(eva.eva_tracing))

    implementation(libs.kotlin.stdlib)

    implementation(libs.postgres)
    implementation(libs.jooq)

    testImplementation(libs.opentelemetry.sdk.testing)
    testImplementation(testFixtures(project(eva.eva_persistence)))
}
