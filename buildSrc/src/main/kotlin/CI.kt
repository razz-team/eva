object Ci {

    private const val snapshotBase = "0.0.3"

    private val releaseVersion = System.getenv("RELEASE_VERSION")
    private val githubSha = System.getenv("GITHUB_SHA") ?: "latest"

    val isRelease = releaseVersion != null || releaseVersion == ""
    val publishVersion = releaseVersion ?: "$snapshotBase-$githubSha-SNAPSHOT"
}
