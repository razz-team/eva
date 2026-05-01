package com.razz.eva.repository

import com.razz.eva.paging.Page
import com.razz.eva.paging.PagedList
import com.razz.eva.persistence.executor.QueryExecutor
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.Table

/**
 * Shared body of [JooqBaseModelRepository.findPage] and [JooqBaseEntityRepository.findPage].
 *
 * If `page` is `Page.Next`, generates SQL of the form:
 * ```
 * SELECT * FROM <table>
 * WHERE <condition> AND (<ordering>, <id>) > (<maxOrdering>, <offset>)
 * ORDER BY <ordering>, <id>
 * LIMIT pageSize
 * ```
 */
internal suspend fun <ID, N, S, P, R> executeFindPage(
    queryExecutor: QueryExecutor,
    dslContext: DSLContext,
    table: Table<R>,
    condition: Condition,
    page: Page<P>,
    pagingStrategy: PagingStrategy<ID, N, S, P, R>,
    mapper: (R) -> N,
): PagedList<S, P> where ID : Comparable<ID>, S : N, P : Comparable<P>, R : Record {
    val pagedSelect = pagingStrategy.select(
        dslContext.selectFrom(table).where(condition),
        page,
    )
    val records = queryExecutor.executeSelect(
        dslContext = dslContext,
        jooqQuery = pagedSelect,
        table = table,
    )
    return pagingStrategy.pagedList(records, mapper, page.size)
}
