package com.razz.eva.paging

import com.razz.eva.paging.Page.First
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

abstract class Pages<ORDER_BY : Comparable<ORDER_BY>, ELEMENT : Any>(
    private val batchSize: Int,
) {

    protected abstract suspend fun batch(page: Page<ORDER_BY>): PagedList<ELEMENT, ORDER_BY>

    fun asFlow(page: Page<ORDER_BY>? = null): Flow<ELEMENT> = flow {
        asBatchFlow(page ?: First(Size(batchSize)))
            .collect { batch -> batch.forEach { emit(it) } }
    }

    fun asBatchFlow(page: Page<ORDER_BY>? = null): Flow<List<ELEMENT>> =
        flow { emitBatch(page ?: First(Size(batchSize))) }

    private tailrec suspend fun FlowCollector<List<ELEMENT>>.emitBatch(page: Page<ORDER_BY>?) {
        if (page != null) {
            val batch = batch(page)
            emit(batch)
            emitBatch(batch.nextPage())
        } else return
    }
}
