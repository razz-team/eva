object Ci {

    private const val snapshotBase = "0.0.2"

    private val releaseVersion = System.getenv("RELEASE_VERSION")

    val isRelease = releaseVersion != null
    val publishVersion = releaseVersion ?: "$snapshotBase-SNAPSHOT"
}
