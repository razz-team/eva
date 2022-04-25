plugins {
    id("eva-kotlin")
}

dependencies {
    implementation(project(eva.eva_uow))
    implementation(project(eva.eva_repository))
}
