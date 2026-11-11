import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider
import java.io.File

class LogbackTestConfig(
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val configFile: File,
) : CommandLineArgumentProvider {

    override fun asArguments() = listOf("-Dlogback.configurationFile=${configFile.absolutePath}")
}
