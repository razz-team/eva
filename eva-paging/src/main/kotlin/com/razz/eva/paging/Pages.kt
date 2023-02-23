package com.razz.eva.paging

import com.razz.eva.paging.Page.First
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

abstract class Pages<ORDER_BY : Comparable<ORDER_BY>, ELEMENT : Any> (
    private val batchSize: Int
) {

    protected abstract suspend fun batch(page: Page<ORDER_BY>): PagedList<ELEMENT, ORDER_BY>

    fun asFlow(): Flow<ELEMENT> = flow {
        asBatchFlow().collect { batch -> batch.forEach { emit(it) } }
    }

    fun asBatchFlow(): Flow<List<ELEMENT>> = flow { emitBatch(First(Size(batchSize))) }

    private tailrec suspend fun FlowCollector<List<ELEMENT>>.emitBatch(page: Page<ORDER_BY>?) {
        if (page != null) {
            val batch = batch(page)
            emit(batch)
            emitBatch(batch.nextPage())
        } else return
    }
}
