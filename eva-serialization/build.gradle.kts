plugins {
    id("eva-kotlin")
    id("eva-publish")
}

dependencies {
    testImplementation(project(eva.eva_paging))
}