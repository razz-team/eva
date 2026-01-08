plugins {
    id("eva-kotlin")
    id("eva-publish")
}

dependencies {
    api(libs.vertx_pg)

    api(project(eva.eva_persistence))

    implementation(libs.kotlin_stdlib)
    implementation(libs.kotlin_coroutines)

    implementation(libs.vertx_kotlin)
    implementation(libs.vertx_kotlin_coroutines)
    implementation(libs.jooq)
    implementation(libs.jooq_postgres)
}
