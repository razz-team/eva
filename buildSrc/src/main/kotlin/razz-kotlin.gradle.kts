import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("io.gitlab.arturbosch.detekt")
    id("java-library")
    id("java-test-fixtures")
    java
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events(STANDARD_ERROR)
    }
}

tasks.compileTestFixturesKotlin {
    kotlinOptions {
        jvmTarget = versions.jvm
    }
}
tasks.compileTestFixturesJava {
    enabled = false
}

apply<ProjectsPlugin>()

dependencies {
    implementation(platform(libs.kotlinx_coroutines_bom))
    implementation(platform(libs.kotlin_bom))
    implementation(platform(libs.jackson_bom))

    detektPlugins(libs.detekt)

    testImplementation(platform(libs.testcontainers_bom))
    testImplementation(libs.mockk)

    constraints {
        implementation(libs.log4j) {
            because("CVE-2021-44228: Log4j is vulnerable")
        }
    }
}

java.sourceCompatibility = versions.java
java.targetCompatibility = versions.java

tasks.compileKotlin {
    sourceCompatibility = versions.jvm
    targetCompatibility = versions.jvm
    kotlinOptions {
        jvmTarget = versions.jvm
        allWarningsAsErrors = true
        freeCompilerArgs = listOf("-Xopt-in=kotlin.RequiresOptIn")
    }
}

tasks.compileTestKotlin {
    sourceCompatibility = versions.jvm
    targetCompatibility = versions.jvm
    kotlinOptions {
        jvmTarget = versions.jvm
        freeCompilerArgs = listOf("-Xopt-in=kotlin.RequiresOptIn")
    }
}

tasks.jar {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

detekt {
    parallel = true
    config = files("$rootDir/buildSrc/src/main/resources/detekt-config.yml")
    reports {
        html.enabled = false
        xml.enabled = false
        txt.enabled = false
        sarif.enabled = false
    }
}
