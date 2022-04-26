plugins {
    id("eva-kotlin")
    id("eva-publish")
}

dependencies {
    api(libs.hikari)
    api(libs.kotlin_coroutines)

    api(project(eva.eva_persistence))

    implementation(libs.kotlin_stdlib)

    implementation(libs.postgres)
    implementation(libs.jooq)
}
