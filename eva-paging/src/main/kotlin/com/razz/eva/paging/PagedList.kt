package com.razz.eva.paging

interface PagedList<Element, OrderBy : Comparable<OrderBy>> : List<Element> {

    fun nextPage(): Page.Next<OrderBy>?
}
