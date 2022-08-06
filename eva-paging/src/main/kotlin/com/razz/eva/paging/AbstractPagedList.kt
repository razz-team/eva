package com.razz.eva.paging

abstract class AbstractPagedList<I, P>(
    private val list: List<I>,
    private val pageSize: Size
) : PagedList<I, P>, List<I> by list {

    protected abstract fun maxPivot(item: I): P

    protected abstract fun offset(item: I): ModelOffset

    override fun nextPage(): Page.Next<P>? {
        return if (list.size >= pageSize.intValue() && list.isNotEmpty()) {
            val lastElement = list.last()
            Page.Next(
                maxPivot = maxPivot(lastElement),
                modelIdOffset = offset(lastElement),
                size = pageSize
            )
        } else null
    }
}
