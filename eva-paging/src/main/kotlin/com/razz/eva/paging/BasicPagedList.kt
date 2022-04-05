package com.razz.eva.paging

data class BasicPagedList<I>(
    private val list: List<I>,
    private val nextPage: TimestampPage.Next?
) : PagedList<I>, List<I> by list {
    override fun nextPage(): TimestampPage.Next? = nextPage
}
