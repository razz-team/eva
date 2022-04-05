package com.razz.eva.migrations

sealed class DbSchema(private val schema: String) {

    override fun toString() = schema

    object ModelsSchema : DbSchema("public")
    object EventsSchema : DbSchema("events")
}
