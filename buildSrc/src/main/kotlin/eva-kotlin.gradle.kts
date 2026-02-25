import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR

plugins {
    java
    kotlin("jvm")
    id("java-library")
    id("java-test-fixtures")
    id("io.gitlab.arturbosch.detekt")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

tasks.test {
    useJUnitPlatform()
    testLogging {
        events(STANDARD_ERROR)
    }
}

apply<ProjectsPlugin>()

dependencies {
    detektPlugins(libs.detekt.formatting)

    api(libs.kotlinx.serialization)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlin.coroutines)

    testImplementation(libs.mockk)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.assertions.json)
    testImplementation(libs.kotest.runner)
}

tasks.compileJava {
    options.release.set(21)
}
tasks.compileKotlin {
    compilerOptions {
        apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2
        languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
        allWarningsAsErrors = true
        freeCompilerArgs = listOf("-opt-in=kotlin.RequiresOptIn", "-Xconsistent-data-class-copy-visibility")
    }
}
tasks.compileTestJava {
    options.release.set(21)
}
tasks.compileTestKotlin {
    compilerOptions {
        apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2
        languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
        freeCompilerArgs = listOf("-opt-in=kotlin.RequiresOptIn")
    }
}
tasks.compileTestFixturesJava {
    enabled = true
    options.release.set(21)
}
tasks.compileTestFixturesKotlin {
    compilerOptions {
        apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2
        languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
    }
}

tasks.jar {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

detekt {
    autoCorrect = true
    parallel = true
    config.setFrom(files("$rootDir/buildSrc/src/main/resources/detekt-config.yml"))
}

tasks.detekt {
    jvmTarget = "18" // detekt is not ready for 21
    reports {
        html.required.set(false)
        xml.required.set(false)
        txt.required.set(false)
        sarif.required.set(false)
    }
}
