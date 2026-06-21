plugins {
    id("eva-kotlin")
    id("eva-publish")
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.jooq)
    implementation(libs.kotlin.coroutines)

    api(project(eva.eva_domain))
    api(project(eva.eva_idempotency_key))
}
