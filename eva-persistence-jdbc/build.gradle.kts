plugins {
    id("eva-kotlin")
    id("eva-publish")
}

dependencies {
    api(libs.hikari)
    api(libs.kotlin_coroutines)

    api(project(eva.eva_persistence))
    api(project(eva.eva_tracing))

    implementation(libs.kotlin_stdlib)

    implementation(libs.postgres)
    implementation(libs.jooq)

    implementation(libs.opentracing_api)
    implementation(libs.opentracing_noop)
}
