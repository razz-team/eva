package com.razz.eva.paging

import java.time.Instant

abstract class AbstractPagedList<I>(
    private val list: List<I>,
    private val pageSize: Size
) : PagedList<I>, List<I> by list {

    protected abstract fun maxTimestamp(item: I): Instant

    protected abstract fun offset(item: I): ModelOffset

    override fun nextPage(): TimestampPage.Next? {
        return if (list.size >= pageSize.intValue() && list.isNotEmpty()) {
            val lastElement = list.last()
            TimestampPage.Next(
                maxTimestamp = maxTimestamp(lastElement),
                modelIdOffset = offset(lastElement),
                size = pageSize
            )
        } else null
    }
}
