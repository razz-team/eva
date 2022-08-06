package com.razz.eva.paging

interface PagedList<I, P> : List<I> {

    fun nextPage(): Page.Next<P>?
}
