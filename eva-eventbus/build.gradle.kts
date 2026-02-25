plugins {
    id("eva-kotlin")
    id("eva-publish")
}

dependencies {

    api(libs.kotlin.coroutines)
    api(project(eva.eva_domain))
    api(project(eva.eva_events))
    implementation(libs.kotlin.logging)
    implementation(libs.kotlin.coroutines.slf4j)
    implementation(libs.logback)
    implementation(project(eva.eva_serialization))
}
