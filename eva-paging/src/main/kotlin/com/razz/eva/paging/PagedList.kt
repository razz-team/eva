package com.razz.eva.paging

interface PagedList<I> : List<I> {

    fun nextPage(): TimestampPage.Next?
}
