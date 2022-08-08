package com.razz.eva.migrations

data class Migration(val path: String, val schema: DbSchema) {

    fun classpathLocation() = "classpath:$path"

    override fun toString() = """
        Migration[
            path=$path,
            schema=$schema
        ]
    """.trimIndent()

    companion object Factory {

        fun modelsMigration(path: String): Migration {
            check(!path.contains("events"))
            check(!path.contains("locks"))
            return Migration(path, DbSchema.ModelsSchema)
        }

        val EventsMigration = Migration("com/razz/eva/events/db", DbSchema.EventsSchema)
    }
}
