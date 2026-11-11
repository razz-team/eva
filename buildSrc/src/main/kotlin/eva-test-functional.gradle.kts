import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_OUT
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
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
        events(FAILED, STANDARD_OUT, STANDARD_ERROR)
        exceptionFormat = TestExceptionFormat.FULL
        showCauses = true
        showStackTraces = true
    }
    jvmArgumentProviders.add(LogbackTestConfig(rootProject.file("buildSrc/src/main/resources/logback-test.xml")))
}

tasks.check { dependsOn(testFunctional) }

tasks.named<KotlinCompile>("compileTestFunctionalKotlin") {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks.named("compileTestFunctionalJava") {
    enabled = false
}
