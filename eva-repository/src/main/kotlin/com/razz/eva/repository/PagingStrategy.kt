package com.razz.eva.repository

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelId
import com.razz.eva.paging.BasicPagedList
import com.razz.eva.paging.ModelOffset
import com.razz.eva.paging.Page
import com.razz.eva.paging.PagedList
import com.razz.eva.paging.Size
import kotlin.reflect.KClass
import org.jooq.Record
import org.jooq.Select
import org.jooq.SelectOrderByStep
import org.jooq.TableField

abstract class PagingStrategy<ID, MID, M, S, P, R>(
    private val modelClass: KClass<S>
) where ID : Comparable<ID>,
        MID : ModelId<ID>,
        M : Model<MID, *>,
        S : M,
        P : Comparable<P>,
        R : Record {

    protected abstract fun tablePivot(): TableField<R, P>

    protected abstract fun tableId(): TableField<R, ID>

    protected abstract fun tableOffset(modelOffset: ModelOffset): ID

    protected abstract fun modelPivot(model: S): P

    protected abstract fun modelOffset(model: S): ModelOffset

    internal fun select(
        step: SelectOrderByStep<R>,
        page: Page<P>
    ): Select<R> = step.orderBy(tablePivot().desc(), tableId())
        .apply {
            if (page is Page.Next<P>) {
                seek(page.maxPivot, tableOffset(page.modelIdOffset))
            }
        }
        .limit(page.sizeValue())

    internal fun pagedList(list: List<M>, pageSize: Size): PagedList<S, P> {
        val filtered = list.filterIsInstance(modelClass.java)
        return BasicPagedList(filtered, nextPage(filtered, pageSize))
    }

    private fun nextPage(list: List<S>, pageSize: Size) = if (list.size >= pageSize.intValue() && list.isNotEmpty()) {
        val lastElement = list.last()
        Page.Next(
            maxPivot = modelPivot(lastElement),
            modelIdOffset = modelOffset(lastElement),
            size = pageSize
        )
    } else null
}
