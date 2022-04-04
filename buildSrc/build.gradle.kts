

plugins {
    kotlin("jvm") version "1.5.31"
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

tasks.compileKotlin {
    sourceCompatibility = versions.jvm
    targetCompatibility = versions.jvm
    kotlinOptions {
        languageVersion = versions.kotlin
        jvmTarget = versions.jvm
    }
}

dependencies {
    implementation(libs.kotlin_serialization)
    implementation(libs.shadow_jar_plugin)
    implementation(libs.detekt_plugin)
    implementation(libs.kotlin_plugin)
}
