object Ci {

    private const val SNAPSHOT_BASE = "0.4.0"
    private const val RELEASE_VERSION = "0.3.0"
    private val githubSha = System.getenv("GITHUB_SHA") ?: "latest"

    val publishRelease = true // System.getenv("PUBLISH_RELEASE")?.let(::parseBoolean) ?: false
    val publishVersion = if (publishRelease) RELEASE_VERSION else "$SNAPSHOT_BASE-$githubSha-SNAPSHOT"
}
