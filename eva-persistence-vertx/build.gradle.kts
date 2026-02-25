plugins {
    id("eva-kotlin")
    id("eva-publish")
}

dependencies {
    api(libs.vertx.pg)

    api(project(eva.eva_persistence))

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.coroutines)

    implementation(libs.vertx.kotlin)
    implementation(libs.vertx.kotlin.coroutines)
    implementation(libs.jooq)
    implementation(libs.jooq.postgres)
}
