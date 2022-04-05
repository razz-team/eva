package com.razz.jooq.converter

import org.jooq.Converter
import java.sql.Date
import java.time.LocalDate

class LocalDateConverter : Converter<Date, LocalDate> {

    override fun from(date: Date?): LocalDate? {
        return date?.toLocalDate()
    }

    override fun to(localDate: LocalDate?): Date? {
        return localDate?.let(Date::valueOf)
    }

    override fun fromType(): Class<Date> {
        return Date::class.java
    }

    override fun toType(): Class<LocalDate> {
        return LocalDate::class.java
    }

    companion object {
        val instance = LocalDateConverter()
    }
}
