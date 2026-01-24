package com.razz.eva.paging

class TransformablePagedList<ELEMENT, ORDER_BY : Comparable<ORDER_BY>, TRANSFORMED>(
    private val pagedList: PagedList<ELEMENT, ORDER_BY>,
    private val transformation: Function1<ELEMENT, TRANSFORMED?>,
) : PagedList<ELEMENT, ORDER_BY> by pagedList {

    fun transform() = pagedList.mapNotNull(transformation)
}
