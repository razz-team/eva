import java.net.URI

plugins {
    id("maven-publish")
}

publishing {
    repositories {
        maven {
            name = "MavenCentral"
            url = if (Ci.isRelease) {
                URI("https://s01.oss.sonatype.org/content/repositories/releases/")
            } else {
                URI("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            }
            credentials {
                username = System.getenv("SONATYPE_USER")
                password = System.getenv("SONATYPE_PASS")
            }
        }
    }
    publications {
        register<MavenPublication>("eva") {
            from(components["java"])
        }
    }
}
