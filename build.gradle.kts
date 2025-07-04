plugins {
    id("com.github.ben-manes.versions") version "0.52.0"
    id("io.github.gradle-nexus.publish-plugin") version versions.nexus_publish_plugin
}

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return isStable.not()
}

tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {
    rejectVersionIf {
        isNonStable(candidate.version)
    }
}

allprojects {
    group = "team.razz.eva"
    version = Ci.publishVersion
}

nexusPublishing {
    repositories {
        sonatype()
    }
//    repositories {
//        // see https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/#configuration
//        sonatype {
//            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
//            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
//        }
//    }
}
