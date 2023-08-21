plugins {
    id("eva-kotlin")
    id("eva-publish")
}

dependencies {

    api(project(eva.eva_uow))
    implementation(project(eva.eva_serialization))
}
