import java.net.URI

plugins {
    id("maven-publish")
    id("java")
}

val sourcesJar by tasks.creating(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.getByName("main").allSource)
}

publishing {
    repositories {
        maven {
            name = "MavenCentral"
            url = if (Ci.publishRelease) {
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
            val javaComponent = (components["java"] as AdhocComponentWithVariants)
            from(javaComponent)
            artifact(sourcesJar)
            groupId = "team.razz.eva"
            version = Ci.publishVersion
        }
    }
}
