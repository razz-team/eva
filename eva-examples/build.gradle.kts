plugins {
    id("eva-kotlin")
}

dependencies {
    implementation(project(eva.eva_uow))
    implementation(project(eva.eva_repository))
    implementation(project(eva.eva_persistence_jdbc))
}
