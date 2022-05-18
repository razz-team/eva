plugins {
    id("eva-kotlin")
    id("eva-publish")
}

dependencies {

    api(libs.kotlin_coroutines)
    api(project(eva.eva_domain))
    implementation(libs.kotlin_logging)
    implementation(project(eva.eva_serialization))
}
