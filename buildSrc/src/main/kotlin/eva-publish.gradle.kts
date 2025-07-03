import java.net.URI

plugins {
    id("maven-publish")
    id("java")
    id("io.github.gradle-nexus.publish-plugin")
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.getByName("main").allSource)
}

publishing {
    sonatype()
    // repositories {
    //     maven {
    //         name = "MavenCentral"
    //         url = if (Ci.publishRelease) {
    //             URI("https://s01.oss.sonatype.org/content/repositories/releases/")
    //         } else {
    //             URI("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    //         }
    //         credentials {
    //             username = System.getenv("SONATYPE_TOKEN")
    //             password = System.getenv("SONATYPE_TOKEN_PASS")
    //         }
    //     }
    // }
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

nexusPublishing {
    repositories {
        create("MavenCentral") {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))

            username = System.getenv("SONATYPE_TOKEN")
            password = System.getenv("SONATYPE_TOKEN_PASS")
        }
    }
}
