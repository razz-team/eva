package com.razz.eva.paging

import com.razz.eva.paging.Page.First
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

abstract class Pages<OrderBy : Comparable<OrderBy>, Element : Any> (
    private val batchSize: Int
) {

    protected abstract suspend fun batch(page: Page<OrderBy>): PagedList<Element, OrderBy>

    fun asFlow(): Flow<Element> = flow {
        asBatchFlow().collect { batch -> batch.forEach { emit(it) } }
    }

    fun asBatchFlow(): Flow<List<Element>> = flow { emitBatch(First(Size(batchSize))) }

    private tailrec suspend fun FlowCollector<List<Element>>.emitBatch(page: Page<OrderBy>?) {
        if (page != null) {
            val batch = batch(page)
            emit(batch)
            emitBatch(batch.nextPage())
        } else return
    }
}
