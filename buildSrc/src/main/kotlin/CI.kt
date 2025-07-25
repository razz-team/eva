import java.lang.Boolean.parseBoolean

object Ci {

    private const val SNAPSHOT_BASE = "0.15.0"
    private const val RELEASE_VERSION = "0.14.0"
    private val githubSha = System.getenv("GITHUB_SHA") ?: "latest"

    val publishRelease = System.getProperty("release", "true").let(::parseBoolean)
    val publishVersion = if (publishRelease) RELEASE_VERSION else "$SNAPSHOT_BASE-$githubSha-SNAPSHOT"
}
