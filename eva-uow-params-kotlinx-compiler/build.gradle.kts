plugins {
    kotlin("jvm")
    id("java-library")
    id("eva-publish")
}

dependencies {
    compileOnly(libs.kotlin_compiler_embeddable)
}
