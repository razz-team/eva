package com.razz.eva.repository

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelId
import com.razz.eva.paging.BasicPagedList
import com.razz.eva.paging.ModelOffset
import com.razz.eva.paging.PagedList
import com.razz.eva.paging.Size
import com.razz.eva.paging.TimestampPage
import org.jooq.Record
import org.jooq.Select
import org.jooq.SelectOrderByStep
import org.jooq.TableField
import java.time.Instant
import kotlin.reflect.KClass

abstract class PagingStrategy<ID, MID, M, S, R>(
    private val modelClass: KClass<S>
) where ID : Comparable<ID>,
        MID : ModelId<ID>,
        M : Model<MID, *>,
        S : M,
        R : Record {

    protected abstract fun tableTimestamp(): TableField<R, Instant>

    protected abstract fun tableId(): TableField<R, ID>

    protected abstract fun tableOffset(modelOffset: ModelOffset): ID

    protected abstract fun modelTimestamp(model: S): Instant

    protected abstract fun modelOffset(model: S): ModelOffset

    internal fun select(
        step: SelectOrderByStep<R>,
        page: TimestampPage
    ): Select<R> = step.orderBy(tableTimestamp().desc(), tableId())
        .apply {
            if (page is TimestampPage.Next) {
                seek(page.maxTimestamp, tableOffset(page.modelIdOffset))
            }
        }
        .limit(page.sizeValue())

    internal fun pagedList(list: List<M>, pageSize: Size): PagedList<S> {
        val filtered = list.filterIsInstance(modelClass.java)
        return BasicPagedList(filtered, nextPage(filtered, pageSize))
    }

    private fun nextPage(list: List<S>, pageSize: Size) = if (list.size >= pageSize.intValue() && list.isNotEmpty()) {
        val lastElement = list.last()
        TimestampPage.Next(
            maxTimestamp = modelTimestamp(lastElement),
            modelIdOffset = modelOffset(lastElement),
            size = pageSize
        )
    } else null
}
