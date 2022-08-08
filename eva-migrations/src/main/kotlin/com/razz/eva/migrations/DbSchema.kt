package com.razz.eva.migrations

data class DbSchema(private val schema: String) {

    override fun toString() = schema

    companion object Factory {
        val ModelsSchema = DbSchema("public")
        val EventsSchema = DbSchema("events")
    }
}
