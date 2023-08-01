import java.lang.Boolean.parseBoolean

object Ci {

    private const val SNAPSHOT_BASE = "0.3.0"
    private const val RELEASE_VERSION = "0.2.0"
    private val githubSha = System.getenv("GITHUB_SHA") ?: "latest"

    val publishRelease = System.getenv("PUBLISH_RELEASE")?.let(::parseBoolean) ?: false
    val publishVersion = if (publishRelease) RELEASE_VERSION else "$SNAPSHOT_BASE-$githubSha-SNAPSHOT"
}
