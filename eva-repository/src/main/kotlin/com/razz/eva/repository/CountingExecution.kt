package com.razz.eva.repository

import com.razz.eva.persistence.executor.QueryExecutor
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.Table
import org.jooq.TableField
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType

internal val LONG_COUNT = DSL.field("count(*)", SQLDataType.BIGINT)

/**
 * Shared body of [JooqBaseModelRepository.countByGroup] and [JooqBaseEntityRepository.countByGroup].
 *
 * Generates SQL of the form:
 * ```
 * SELECT <groupField>, count(*) FROM <table>
 * WHERE <condition>
 * GROUP BY <groupField>
 * ```
 */
internal suspend fun <K, R : Record> executeCountByGroup(
    queryExecutor: QueryExecutor,
    dslContext: DSLContext,
    table: Table<R>,
    groupField: TableField<R, K>,
    condition: Condition,
): Map<K, Long> {
    val select = dslContext.select(groupField, LONG_COUNT)
        .from(table)
        .where(condition)
        .groupBy(groupField)
    return queryExecutor.executeSelect(
        dslContext = dslContext,
        jooqQuery = select,
        table = select.asTable(),
    ).associate { it.value1() to (it.value2() ?: 0) }
}
