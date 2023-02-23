package com.razz.eva.paging

abstract class AbstractPagedList<ELEMENT, ORDER_BY : Comparable<ORDER_BY>>(
    private val list: List<ELEMENT>,
    private val pageSize: Size
) : PagedList<ELEMENT, ORDER_BY>, List<ELEMENT> by list {

    protected abstract fun maxOrdering(item: ELEMENT): ORDER_BY

    protected abstract fun offset(item: ELEMENT): ModelOffset

    override fun nextPage(): Page.Next<ORDER_BY>? {
        return if (list.size >= pageSize.intValue() && list.isNotEmpty()) {
            val lastElement = list.last()
            Page.Next(
                maxOrdering = maxOrdering(lastElement),
                modelIdOffset = offset(lastElement),
                size = pageSize
            )
        } else null
    }
}
