package com.razz.jooq.binding

import org.jooq.Binding
import org.jooq.BindingGetResultSetContext
import org.jooq.BindingGetSQLInputContext
import org.jooq.BindingGetStatementContext
import org.jooq.BindingRegisterContext
import org.jooq.BindingSQLContext
import org.jooq.BindingSetSQLOutputContext
import org.jooq.BindingSetStatementContext
import org.jooq.Converter
import org.jooq.impl.DSL
import java.sql.SQLFeatureNotSupportedException
import java.sql.Types.VARCHAR
import java.util.*

class InetBinding : Binding<Any, String> {

    override fun converter() = object : Converter<Any, String> {

        override fun from(databaseObject: Any?): String? {
            return databaseObject?.toString()
        }

        override fun to(userObject: String?): Any? {
            return userObject
        }

        override fun fromType(): Class<Any> {
            return Any::class.java
        }

        override fun toType(): Class<String> {
            return String::class.java
        }
    }

    override fun sql(ctx: BindingSQLContext<String>) {
        ctx.render().visit(DSL.`val`(ctx.convert(converter()).value())).sql("::inet")
    }

    override fun register(ctx: BindingRegisterContext<String>) {
        ctx.statement().registerOutParameter(ctx.index(), VARCHAR)
    }

    override fun set(ctx: BindingSetStatementContext<String>) {
        ctx.statement().setString(ctx.index(), Objects.toString(ctx.convert(converter()).value(), null))
    }

    override fun set(ctx: BindingSetSQLOutputContext<String>) {
        throw SQLFeatureNotSupportedException()
    }

    override fun get(ctx: BindingGetResultSetContext<String>) {
        ctx.convert(converter()).value(ctx.resultSet().getString(ctx.index()))
    }

    override fun get(ctx: BindingGetStatementContext<String>) {
        ctx.convert(converter()).value(ctx.statement().getString(ctx.index()))
    }

    override fun get(ctx: BindingGetSQLInputContext<String>) {
        throw SQLFeatureNotSupportedException()
    }

    companion object {
        val instance = InetBinding()
    }
}
