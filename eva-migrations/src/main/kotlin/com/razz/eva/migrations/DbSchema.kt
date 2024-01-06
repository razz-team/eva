package com.razz.eva.migrations

data class DbSchema(private val schema: String, private val createOnMigration: Boolean = true) {

    fun stringValue() = schema
    fun createOnMigration() = createOnMigration
    override fun toString() = "$schema (createOnMigration=$createOnMigration)"

    companion object Factory {
        val ModelsSchema = DbSchema("public")
        val EventsSchema = DbSchema("events")
    }
}
