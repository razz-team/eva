package com.razz.eva.paging

interface PagedList<I, P : Comparable<P>> : List<I> {

    fun nextPage(): Page.Next<P>?
}
