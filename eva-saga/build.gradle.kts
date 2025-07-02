plugins {
    id("eva-kotlin")
    id("eva-publish")
}

dependencies {
    implementation(project(eva.eva_domain))
    api(project(eva.eva_tracing))
}
