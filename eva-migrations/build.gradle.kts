plugins {
    id("eva-kotlin")
    id("eva-publish")
}

dependencies {
    implementation(project(eva.eva_persistence))

    implementation(libs.kotlin_stdlib)
    implementation(libs.flyway)
}
