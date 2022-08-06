package com.razz.eva.paging

data class BasicPagedList<I, P>(
    private val list: List<I>,
    private val nextPage: Page.Next<P>?
) : PagedList<I, P>, List<I> by list {
    override fun nextPage(): Page.Next<P>? = nextPage
}
