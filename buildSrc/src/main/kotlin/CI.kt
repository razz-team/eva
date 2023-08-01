object Ci {

    private const val snapshotBase = "0.0.3"

    private val releaseVersion = "0.1.0" // used to be System.getenv("RELEASE_VERSION")
    private val githubSha = System.getenv("GITHUB_SHA") ?: "latest"

    val isRelease = !releaseVersion.isNullOrEmpty()
    val publishVersion = releaseVersion ?: "$snapshotBase-$githubSha-SNAPSHOT"
}
