plugins {
    id("eva-kotlin")
    id("eva-publish")
}

dependencies {
    api(libs.hikari)
    api(libs.kotlin.coroutines)

    api(project(eva.eva_persistence))
    api(project(eva.eva_tracing))

    implementation(libs.kotlin.stdlib)

    implementation(libs.postgres)
    implementation(libs.jooq)
}
