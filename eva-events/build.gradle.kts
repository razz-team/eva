plugins {
    id("eva-kotlin")
    id("eva-publish")
}

dependencies {

    api(project(eva.eva_domain))
    implementation(project(eva.eva_serialization))
}
