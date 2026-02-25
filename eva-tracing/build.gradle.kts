plugins {
    id("eva-kotlin")
    id("eva-publish")
}

dependencies {
    api(libs.opentelemetry.sdk)
    api(libs.opentelemetry.kotlin)
    implementation(libs.opentelemetry.sdk.testing)
    implementation(libs.jooq)
    implementation(libs.kotlin.coroutines)
}
