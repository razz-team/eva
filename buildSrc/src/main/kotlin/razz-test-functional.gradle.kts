import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
}

sourceSets {
    create("testFunctional") {
        compileClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
        runtimeClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
    }
}

configurations["testFunctionalImplementation"]
    .extendsFrom(configurations.testImplementation.get())

val testFunctional = tasks.register<Test>("testFunctional") {

    useJUnitPlatform()

    description = "Run functional tests."
    group = "verification"

    testClassesDirs = sourceSets["testFunctional"].output.classesDirs
    classpath = sourceSets["testFunctional"].runtimeClasspath

    testLogging {
        events(STANDARD_ERROR)
    }
}

tasks.check { dependsOn(testFunctional) }

tasks.named<KotlinCompile>("compileTestFunctionalKotlin") {
    kotlinOptions {
        jvmTarget = versions.jvm
    }
}

tasks.named("compileTestFunctionalJava") {
    enabled = false
}
