import com.vanniktech.maven.publish.SonatypeHost

plugins {
    id("org.jetbrains.dokka")
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

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
