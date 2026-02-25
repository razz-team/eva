import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("eva-kotlin")
    id("eva-publish")
}

tasks.withType<KotlinCompile>().configureEach {
    friendPaths.from(
        rootProject.project("eva-repository").layout.buildDirectory.dir("classes/kotlin/main"),
    )
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

    implementation(libs.kotlin.reflect)
    implementation(libs.postgres)
    implementation(libs.kotest.runner)
    implementation(libs.mockk)
    implementation(libs.hikari)
    implementation(libs.testcontainers)
    implementation(libs.testcontainers.postgres)
    implementation(libs.kotlin.logging)
    implementation(libs.flyway)
    implementation(libs.flyway.postgres)
    implementation(libs.opentelemetry.sdk.testing)

    testImplementation(testFixtures(project(eva.eva_repository)))
    testImplementation(testFixtures(project(eva.eva_domain)))
}
