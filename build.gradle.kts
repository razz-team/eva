plugins {
    alias(libs.plugins.versions)
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
    version = System.getenv("RELEASE_VERSION") ?: System.getenv("GITHUB_SHA")?.let { "${it.take(7)}-SNAPSHOT" } ?: "1.0-SNAPSHOT"
}
