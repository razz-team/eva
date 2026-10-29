package com.razz.eva.outbox.app

import com.charleskorn.kaml.AnchorsAndAliases
import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.YamlTaggedNode
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads [ApplicationConfig] from YAML. `${NAME}` placeholders in values are replaced with environment variables,
 * so secrets don't have to be kept in the file.
 */
object ConfigLoader {

    private val yaml = Yaml(
        configuration = YamlConfiguration(
            strictMode = true,
            polymorphismStyle = PolymorphismStyle.Property,
            // lets one database block be shared, e.g. between the scheduler and a task
            anchorsAndAliases = AnchorsAndAliases.Permitted(),
        ),
    )
    private val placeholder = Regex("""\$\{([A-Za-z_][A-Za-z0-9_]*)}""")

    fun load(path: Path, env: Map<String, String> = System.getenv()): ApplicationConfig =
        parse(Files.readString(path), env)

    fun parse(text: String, env: Map<String, String> = System.getenv()): ApplicationConfig {
        // substituted in the parsed tree, so values need no YAML escaping
        val resolved = yaml.parseToYamlNode(text).resolve(env)
        return yaml.decodeFromYamlNode(ApplicationConfig.serializer(), resolved)
    }

    private fun YamlNode.resolve(env: Map<String, String>): YamlNode = when (this) {
        is YamlScalar -> copy(content = placeholder.replace(content) { match ->
            val name = match.groupValues[1]
            requireNotNull(env[name]) { "Environment variable $name is not set" }
        })
        is YamlMap -> copy(entries = entries.mapValues { (_, value) -> value.resolve(env) })
        is YamlList -> copy(items = items.map { it.resolve(env) })
        is YamlTaggedNode -> copy(innerNode = innerNode.resolve(env))
        else -> this
    }
}
