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

java.sourceCompatibility = JavaVersion.VERSION_19
java.targetCompatibility = JavaVersion.VERSION_19
tasks.compileJava {
    options.release.set(19)
}

tasks.compileKotlin {
    kotlinOptions {
        jvmTarget = "19"
    }
}
