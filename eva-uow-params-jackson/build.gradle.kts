plugins {
    id("eva-kotlin")
    id("eva-publish")
}

dependencies {
    api(project(eva.eva_uow))
    implementation(libs.jackson_databind)
    implementation(libs.jackson_kotlin)

    testImplementation(testFixtures(project(eva.eva_domain)))
}
