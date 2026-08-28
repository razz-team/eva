plugins {
    id("eva-kotlin")
    id("eva-publish")
}

dependencies {
    implementation(project(eva.eva_domain))
    implementation(libs.kotlin.coroutines)
    implementation(libs.kotlin.logging)
    api(project(eva.eva_tracing))

    testImplementation(libs.opentelemetry.sdk.testing)
}
