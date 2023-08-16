package com.razz.eva.repository

import com.razz.eva.paging.BasicPagedList
import com.razz.eva.paging.Offset
import com.razz.eva.paging.Page
import com.razz.eva.paging.PagedList
import com.razz.eva.paging.Size
import org.jooq.Record
import org.jooq.Select
import org.jooq.SelectOrderByStep
import org.jooq.SortOrder
import org.jooq.TableField

abstract class PagingStrategy<ID, M, S, P, R>
    where ID : Comparable<ID>,
          S : M,
          P : Comparable<P>,
          R : Record {

    protected abstract fun tableOrdering(): TableField<R, P>

    protected abstract fun tableId(): TableField<R, ID>

    protected abstract fun tableOffset(offset: Offset): ID

    protected abstract fun ordering(data: S): P

    protected abstract fun offset(data: S): Offset

    protected open fun order(): SortOrder = SortOrder.DESC

    internal fun select(
        step: SelectOrderByStep<R>,
        page: Page<P>
    ): Select<R> = step.orderBy(tableOrdering().sort(order()), tableId())
        .apply {
            if (page is Page.Next<P>) {
                seek(page.maxOrdering, tableOffset(page.offset))
            }
        }
        .limit(page.sizeValue())

    internal fun pagedList(list: List<R>, mapper: (R) -> M, pageSize: Size): PagedList<S, P> {
        val mapped = filter(list, mapper)
        return BasicPagedList(mapped, nextPage(mapped, pageSize))
    }

    open fun filter(list: List<R>, mapper: (R) -> M): List<S> {
        @Suppress("UNCHECKED_CAST")
        return list.mapNotNull { record ->
            mapper(record) as? S
        }
    }

    private fun nextPage(list: List<S>, pageSize: Size) = if (list.size >= pageSize.intValue() && list.isNotEmpty()) {
        val lastElement = list.last()
        Page.Next(
            maxOrdering = ordering(lastElement),
            offset = offset(lastElement),
            size = pageSize
        )
    } else null
}
