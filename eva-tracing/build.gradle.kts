plugins {
    id("eva-kotlin")
    id("eva-publish")
}

dependencies {
    api(libs.opentelemetry_sdk)
    api(libs.opentelemetry_kotlin)
    implementation(libs.opentelemetry_sdk_testing)
    implementation(libs.jooq)
    implementation(libs.kotlin_coroutines)
}
