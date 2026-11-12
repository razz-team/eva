import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.SourcesJar

plugins {
    id("org.jetbrains.dokka")
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    println("Publishing artifact $group:$name:$version")

    publishToMavenCentral()

    // A snapshot publishes its jars only. Each sources or javadoc jar, with its checksums, is more uploads to
    // Sonatype, which answers 429 Too Many Requests when a snapshot has too many. Releases keep both.
    if (version.toString().endsWith("-SNAPSHOT")) {
        plugins.withId("org.jetbrains.kotlin.jvm") {
            configure(KotlinJvm(javadocJar = JavadocJar.None(), sourcesJar = SourcesJar.None()))
        }
    }

    signAllPublications()

    pom {
        name = "eva"
        description = "Kotlin open-source framework, which helps you to write your code in DDD style and using CQRS approach."
        url = "https://github.com/razz-team/eva"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        scm {
            connection = "https://github.com/razz-team/eva"
            url = "https://github.com/razz-team/eva"
        }
        developers {
            developer {
                name = "razz-team"
            }
        }
    }
}
