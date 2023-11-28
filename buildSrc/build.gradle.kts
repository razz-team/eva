plugins {
    kotlin("jvm") version "1.9.21"
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
    kotlinOptions {
        languageVersion = "1.9"
        jvmTarget = versions.jvm
    }
}

dependencies {
    implementation(libs.kotlin_serialization)
    implementation(libs.detekt_plugin)
    implementation(libs.kotlin_plugin)
}
