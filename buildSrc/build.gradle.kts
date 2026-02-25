plugins {
    kotlin("jvm") version "2.1.0"
    `java-gradle-plugin`
    `kotlin-dsl`
    `kotlin-dsl-precompiled-script-plugins`
}

java.sourceCompatibility = JavaVersion.VERSION_21
java.targetCompatibility = JavaVersion.VERSION_21
tasks.compileJava {
    options.release.set(21)
}

tasks.compileKotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
    }
}

dependencies {
    implementation(libs.kotlin.serialization.plugin)
    implementation(libs.detekt.plugin)
    implementation(libs.kotlin.plugin)
    implementation(libs.dokka.plugin)
    implementation(libs.maven.publish.plugin)

    // make version catalog accessors available to precompiled script plugins
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
