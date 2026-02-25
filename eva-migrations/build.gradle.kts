plugins {
    id("eva-kotlin")
    id("eva-publish")
}

dependencies {
    implementation(project(eva.eva_persistence))

    implementation(libs.kotlin.stdlib)
    implementation(libs.flyway)
    implementation(libs.flyway.postgres)
    implementation(libs.hikari)
}
