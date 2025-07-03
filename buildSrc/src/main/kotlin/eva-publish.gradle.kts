import java.net.URI

plugins {
    id("maven-publish")
    id("java")
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.getByName("main").allSource)
}

publishing {
    repositories {
        maven {
            name = "MavenCentral"
            url = if (Ci.publishRelease) {
                URI("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/content/repositories/releases/")
            } else {
                URI("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/content/repositories/snapshots/")
            }
            credentials {
                username = System.getenv("SONATYPE_TOKEN")
                password = System.getenv("SONATYPE_TOKEN_PASS")
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
