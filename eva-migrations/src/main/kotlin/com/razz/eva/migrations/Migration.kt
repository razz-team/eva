package com.razz.eva.migrations

sealed class Migration(private val path: String, val schema: DbSchema) {

    fun classpathLocation() = "classpath:$path"

    override fun toString() = """
        Migration[
            path=$path,
            schema=$schema
        ]
    """.trimIndent()

    class ModelsMigration(path: String) : Migration(path, DbSchema.ModelsSchema) {
        init {
            check(!path.contains("events"))
        }
    }

    object EventsMigration : Migration("com/razz/eva/events/db", DbSchema.EventsSchema)
}
