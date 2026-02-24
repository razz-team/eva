import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME

class EvaUowParamsKotlinxCompilerPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.dependencies.add(
            PLUGIN_CLASSPATH_CONFIGURATION_NAME,
            target.dependencies.project(mapOf("path" to ":eva-uow-params-kotlinx-compiler")),
        )
    }
}
