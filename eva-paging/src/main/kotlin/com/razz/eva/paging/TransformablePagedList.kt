package com.razz.eva.paging

class TransformablePagedList<I, O>(
    private val pagedList: PagedList<I>,
    private val transformation: Function1<I, O?>
) : PagedList<I> by pagedList {

    fun transform() = pagedList.mapNotNull(transformation)
}
