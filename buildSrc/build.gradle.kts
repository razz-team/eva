plugins {
    kotlin("jvm") version "2.1.0"
    `java-gradle-plugin`
    `kotlin-dsl`
    `kotlin-dsl-precompiled-script-plugins`
}

repositories {
    mavenLocal()
    mavenCentral()
    gradlePluginPortal()
}

java.sourceCompatibility = versions.java
java.targetCompatibility = versions.java
tasks.compileJava {
    options.release.set(versions.java.majorVersion.toInt())
}

tasks.compileKotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
    }
}

dependencies {
    implementation(libs.kotlin_serialization)
    implementation(libs.detekt_plugin)
    implementation(libs.kotlin_plugin)
}
