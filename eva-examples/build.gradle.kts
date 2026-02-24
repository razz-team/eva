plugins {
    id("eva-kotlin")
}

apply<EvaUowParamsKotlinxCompilerPlugin>()

dependencies {
    implementation(project(eva.eva_uow))
    implementation(project(eva.eva_uow_params_kotlinx))
    implementation(project(eva.eva_uow_params_jackson))
    implementation(project(eva.eva_repository))
    implementation(project(eva.eva_persistence_jdbc))
    implementation(project(eva.eva_paging))

    testImplementation(project(eva.eva_test))
}
