plugins {
    id("eva-kotlin")
    id("eva-publish")
}

dependencies {
    api(libs.opentracing_api)
    api(libs.micrometer)
    implementation(libs.jaeger_client)
    implementation(libs.jaeger_micrometer)
    implementation(libs.kotlin_coroutines)
}
