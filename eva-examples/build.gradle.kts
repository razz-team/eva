plugins {
    id("eva-kotlin")
}

dependencies {
    implementation(project(eva.eva_uow))
    implementation(project(eva.eva_uow_serialization_kotlinx))
    implementation(project(eva.eva_serialization))
    implementation(project(eva.eva_repository))
    implementation(project(eva.eva_persistence_jdbc))

    implementation(project(eva.eva_paging))

    testImplementation(project(eva.eva_test))
}
