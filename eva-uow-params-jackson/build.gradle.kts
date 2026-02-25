plugins {
    id("eva-kotlin")
    id("eva-publish")
}

dependencies {
    api(project(eva.eva_uow))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.kotlin)

    testImplementation(testFixtures(project(eva.eva_domain)))
}
