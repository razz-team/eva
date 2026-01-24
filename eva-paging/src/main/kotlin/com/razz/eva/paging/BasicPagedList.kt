package com.razz.eva.paging

data class BasicPagedList<ELEMENT, ORDER_BY : Comparable<ORDER_BY>>(
    private val list: List<ELEMENT>,
    private val nextPage: Page.Next<ORDER_BY>?,
) : PagedList<ELEMENT, ORDER_BY>, List<ELEMENT> by list {
    override fun nextPage(): Page.Next<ORDER_BY>? = nextPage
}
