plugins {
    id("eva-kotlin")
    id("eva-publish")
}

dependencies {

    api(libs.kotlin_coroutines)
    api(project(eva.eva_domain))
    implementation(libs.kotlin_logging)
    implementation(libs.kotlin_slf4)
    implementation(libs.logback)
    implementation(project(eva.eva_serialization))
}
