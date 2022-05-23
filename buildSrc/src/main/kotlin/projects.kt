import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.kotlin.dsl.project

internal lateinit var rootProject: Project

private fun moduleName(thisRef: eva): String {
    val module = ':' + thisRef::class.qualifiedName!!
        .replace("eva.", "")
        .replace('.', ':')
        .replace('_', '-')
    check(rootProject.findProject(module) != null) {
        "Invalid module name: $module"
    }
    return module
}

fun DependencyHandler.project(module: eva) = this.project(moduleName(module))

interface eva {
    object eva_domain : eva
    object eva_eventbus : eva
    object eva_events : eva
    object eva_events_db_schema : eva
    object eva_jooq : eva
    object eva_migrations : eva
    object eva_paging : eva
    object eva_persistence : eva
    object eva_idempotency_key : eva
    object eva_persistence_jdbc : eva
    object eva_persistence_vertx : eva
    object eva_repository : eva
    object eva_saga : eva
    object eva_serialization : eva
    object eva_tracing : eva
    object eva_test : eva
    object eva_uow : eva
    object eva_examples : eva
}

class ProjectsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        rootProject = project.rootProject
    }
}
