plugins {
    id("eva-kotlin")
}

dependencies {
    api(libs.hikari)

    api(project(eva.eva_persistence))

    implementation(libs.kotlin_stdlib)
    implementation(libs.kotlin_coroutines)

    implementation(libs.postgres)
    implementation(libs.jooq)
}
