package com.razz.eva.repository

import org.jooq.Query
import org.jooq.Record
import java.lang.IllegalStateException

class JooqQueryException(
    val query: Query,
    val records: List<Record>,
    message: String,
) : IllegalStateException(message)
