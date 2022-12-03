import org.gradle.api.JavaVersion.VERSION_17

plugins {
    `kotlin-dsl`
}
repositories.mavenCentral()
sourceSets.main {
    java {
        setSrcDirs(setOf(projectDir.parentFile.resolve("src/main/kotlin")))
        include("dependencies.kt", "projects.kt")
    }
}

java.sourceCompatibility = VERSION_17
java.targetCompatibility = VERSION_17

tasks.compileKotlin {
    kotlinOptions {
        jvmTarget = "17"
    }
}
