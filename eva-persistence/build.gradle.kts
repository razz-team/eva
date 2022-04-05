plugins {
    id("eva-kotlin")
}

dependencies {
    implementation(libs.kotlin_stdlib)
    implementation(libs.jooq)
    implementation(libs.kotlin_coroutines)

    implementation(project(eva.eva_domain))
    implementation(project(eva.eva_idempotency_key))
}
