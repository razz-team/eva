package com.razz.eva.paging

data class BasicPagedList<Element, OrderBy : Comparable<OrderBy>>(
    private val list: List<Element>,
    private val nextPage: Page.Next<OrderBy>?
) : PagedList<Element, OrderBy>, List<Element> by list {
    override fun nextPage(): Page.Next<OrderBy>? = nextPage
}
