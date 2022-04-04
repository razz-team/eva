import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.kotlin.dsl.project

internal lateinit var rootProject: Project

private fun moduleName(thisRef: razz): String {
    val module = ':' + thisRef::class
        .qualifiedName!!
        .replace("razz.", "")
        .replace('.', ':')
        .replace('_', '-')
    check(rootProject.findProject(module) != null) {
        "Invalid module name: $module"
    }
    return module
}

fun DependencyHandler.project(module: razz) = this.project(moduleName(module))

interface razz {
    object eva_domain : razz
    object eva_events : razz
    object eva_events_db_schema : razz
    object eva_jooq : razz
    object eva_paging : razz
    object eva_persistence : razz
    object eva_idempotency_key : razz
    object eva_persistence_jdbc : razz
    object eva_persistence_vertx : razz
    object eva_repository : razz
    object eva_repository_test : razz
    object eva_saga : razz
    object eva_tracing : razz
    object eva_test : razz
    object eva_test_db_schema : razz
    object eva_uow : razz
}

class ProjectsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        rootProject = project.rootProject
    }
}
