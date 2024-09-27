plugins {
    id("eva-kotlin")
    id("eva-publish")
}

dependencies {
    api(libs.opentelemetry_sdk)
    api(libs.opentelemetry_kotlin)
    implementation(libs.jooq)
    implementation(libs.kotlin_coroutines)

    testImplementation(libs.opentelemetry_sdk_testing)
}
