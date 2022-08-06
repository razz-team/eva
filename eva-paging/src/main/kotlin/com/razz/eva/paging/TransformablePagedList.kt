package com.razz.eva.paging

class TransformablePagedList<I, P : Comparable<P>, O>(
    private val pagedList: PagedList<I, P>,
    private val transformation: Function1<I, O?>
) : PagedList<I, P> by pagedList {

    fun transform() = pagedList.mapNotNull(transformation)
}
