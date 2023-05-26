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
    options.release.set(19)
}
tasks.compileKotlin {
    kotlinOptions {
        languageVersion = "1.8"
        jvmTarget = "19"
    }
}