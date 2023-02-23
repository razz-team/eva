package com.razz.eva.paging

class TransformablePagedList<Element, OrderBy : Comparable<OrderBy>, Transformed>(
    private val pagedList: PagedList<Element, OrderBy>,
    private val transformation: Function1<Element, Transformed?>
) : PagedList<Element, OrderBy> by pagedList {

    fun transform() = pagedList.mapNotNull(transformation)
}
