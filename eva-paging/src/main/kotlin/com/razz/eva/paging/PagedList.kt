package com.razz.eva.paging

interface PagedList<ELEMENT, ORDER_BY : Comparable<ORDER_BY>> : List<ELEMENT> {

    fun nextPage(): Page.Next<ORDER_BY>?
}
