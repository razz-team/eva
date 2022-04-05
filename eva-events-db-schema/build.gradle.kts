plugins {
    id("eva-kotlin")
}

dependencies {
    implementation(libs.jooq)
    implementation(project(eva.eva_jooq))
}
