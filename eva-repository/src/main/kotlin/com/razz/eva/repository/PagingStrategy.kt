package com.razz.eva.repository

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelId
import com.razz.eva.paging.BasicPagedList
import com.razz.eva.paging.ModelOffset
import com.razz.eva.paging.Page
import com.razz.eva.paging.PagedList
import com.razz.eva.paging.Size
import org.jooq.Record
import org.jooq.Select
import org.jooq.SelectOrderByStep
import org.jooq.TableField
import kotlin.reflect.KClass

abstract class PagingStrategy<ID, MID, M, S, P, R>(
    private val modelClass: KClass<S>
) where ID : Comparable<ID>,
        MID : ModelId<out Comparable<*>>,
        M : Model<MID, *>,
        S : M,
        P : Comparable<P>,
        R : Record {

    protected abstract fun tableOrdering(): TableField<R, P>

    protected abstract fun tableId(): TableField<R, ID>

    protected abstract fun tableOffset(modelOffset: ModelOffset): ID

    protected abstract fun modelOrdering(model: S): P

    protected abstract fun modelOffset(model: S): ModelOffset

    protected open fun failOnWrongModel(): Boolean = false

    internal fun select(
        step: SelectOrderByStep<R>,
        page: Page<P>
    ): Select<R> = step.orderBy(tableOrdering().desc(), tableId())
        .apply {
            if (page is Page.Next<P>) {
                seek(page.maxOrdering, tableOffset(page.modelIdOffset))
            }
        }
        .limit(page.sizeValue())

    internal fun pagedList(list: List<M>, pageSize: Size): PagedList<S, P> {
        @Suppress("UNCHECKED_CAST")
        val filtered = list.mapNotNull {
            if (modelClass.java.isInstance(it)) {
                it as S
            } else if (failOnWrongModel()) {
                error(
                    "Model ${it.id()} has ${it.javaClass.simpleName} type, " +
                        "while it should have ${modelClass.simpleName} type"
                )
            } else {
                null
            }
        }

        return BasicPagedList(filtered, nextPage(filtered, pageSize))
    }

    private fun nextPage(list: List<S>, pageSize: Size) = if (list.size >= pageSize.intValue() && list.isNotEmpty()) {
        val lastElement = list.last()
        Page.Next(
            maxOrdering = modelOrdering(lastElement),
            modelIdOffset = modelOffset(lastElement),
            size = pageSize
        )
    } else null
}
