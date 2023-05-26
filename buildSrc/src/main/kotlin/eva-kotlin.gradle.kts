import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR

plugins {
    java
    kotlin("jvm")
    id("java-library")
    id("java-test-fixtures")
    id("io.gitlab.arturbosch.detekt")
    id("org.jetbrains.kotlin.plugin.serialization")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events(STANDARD_ERROR)
    }
}

apply<ProjectsPlugin>()

dependencies {
    implementation(platform(libs.kotlinx_coroutines_bom))
    implementation(platform(libs.kotlin_bom))
    implementation(platform(libs.jackson_bom))

    detektPlugins(libs.detekt)

    api(libs.kotlinx_serialization)
    api(libs.kotlinx_serialization_json)
    api(libs.kotlin_coroutines)

    testImplementation(platform(libs.testcontainers_bom))
    testImplementation(libs.mockk)
    testImplementation(libs.kotest_assertions_core)
    testImplementation(libs.kotest_assertions_json)
    testImplementation(libs.kotest_runner)
}

tasks.compileJava {
    options.release.set(versions.java.majorVersion.toInt())
}
tasks.compileKotlin {
    kotlinOptions {
        jvmTarget = versions.jvm
        allWarningsAsErrors = true
        freeCompilerArgs = listOf("-opt-in=kotlin.RequiresOptIn")
    }
}

tasks.compileTestJava {
    options.release.set(versions.java.majorVersion.toInt())
}
tasks.compileTestKotlin {
    kotlinOptions {
        jvmTarget = versions.jvm
        freeCompilerArgs = listOf("-opt-in=kotlin.RequiresOptIn")
    }
}

tasks.compileTestFixturesJava {
    enabled = false
    options.release.set(versions.java.majorVersion.toInt())
}
tasks.compileTestFixturesKotlin {
    kotlinOptions {
        jvmTarget = versions.jvm
    }
}

tasks.jar {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

detekt {
    parallel = true
    config = files("$rootDir/buildSrc/src/main/resources/detekt-config.yml")
}

tasks.detekt {
    jvmTarget = "18" // detekt is not ready for 19
    reports {
        html.required.set(false)
        xml.required.set(false)
        txt.required.set(false)
        sarif.required.set(false)
    }
}
