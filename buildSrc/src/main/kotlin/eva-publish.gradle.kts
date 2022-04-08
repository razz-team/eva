import java.net.URI

plugins {
    id("maven-publish")
}

publishing {
    repositories {
        maven {
            name = "MavenCentral"
            url = URI("https://s01.oss.sonatype.org/content/repositories/releases/")
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
