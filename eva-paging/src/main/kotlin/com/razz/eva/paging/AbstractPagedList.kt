package com.razz.eva.paging

abstract class AbstractPagedList<Element, OrderBy : Comparable<OrderBy>>(
    private val list: List<Element>,
    private val pageSize: Size
) : PagedList<Element, OrderBy>, List<Element> by list {

    protected abstract fun maxOrdering(item: Element): OrderBy

    protected abstract fun offset(item: Element): ModelOffset

    override fun nextPage(): Page.Next<OrderBy>? {
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
