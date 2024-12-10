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

tasks.compileJava {
    options.release.set(21)
}
tasks.compileKotlin {
    kotlinOptions {
        languageVersion = "2.1"
        jvmTarget = "21"
    }
}
