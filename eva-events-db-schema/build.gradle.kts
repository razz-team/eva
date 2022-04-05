plugins {
    id("eva-kotlin")
    id("eva-publish")
}

dependencies {
    implementation(libs.jooq)
    implementation(project(eva.eva_jooq))
}
