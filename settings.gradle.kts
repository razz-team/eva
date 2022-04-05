dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "eva"

rootDir
    .walk()
    .maxDepth(6)
    .filter {
        it.name != "buildSrc"
                && it != rootDir
                && it.isDirectory
                && file("${it.absolutePath}/build.gradle.kts").exists()
    }
    .map {
        it.toRelativeString(rootDir).replace('/', ':')
    }
    .forEach {
        include(it)
    }