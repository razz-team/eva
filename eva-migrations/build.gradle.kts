plugins {
    id("eva-kotlin")
}

dependencies {
    implementation(project(eva.eva_persistence))

    implementation(libs.kotlin_stdlib)
    implementation(libs.flyway)
}
