package com.razz.eva.migrations

data class Migration(val path: String, val schema: DbSchema, val additionalPaths: List<String>) {

    constructor(path: String, schema: DbSchema, vararg additionalPaths: String) :
        this(path, schema, additionalPaths.toList())

    fun classpathLocation() = (listOf(path) + additionalPaths)
        .joinToString(prefix = "classpath:", separator = ",classpath:")

    override fun toString() = """
        Migration[
            path=${(listOf(path) + additionalPaths).joinToString()},
            schema=$schema
        ]
    """.trimIndent()

    companion object Factory {

        fun modelsMigration(path: String, vararg additionalPaths: String): Migration {
            check(!path.contains("events"))
            check(additionalPaths.all { !it.contains("events") })
            return Migration(path, DbSchema.ModelsSchema, additionalPaths.toList())
        }

        val EventsMigration = Migration("com/razz/eva/events/db", DbSchema.EventsSchema)
    }
}
