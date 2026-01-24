package com.razz.eva.repository

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelId
import kotlin.reflect.KClass
import org.jooq.Record

abstract class ModelPagingStrategy<ID, MID, M, S, P, R>(
    private val modelClass: KClass<S>,
) : PagingStrategy<ID, M, S, P, R>() where ID : Comparable<ID>,
      MID : ModelId<out Comparable<*>>,
      M : Model<MID, *>,
      S : M,
      P : Comparable<P>,
      R : Record {

    protected open fun failOnWrongModel(): Boolean = false

    final override fun filter(list: List<R>, mapper: (R) -> M): List<S> {
        @Suppress("UNCHECKED_CAST")
        return list.mapNotNull { record ->
            val mapped = mapper(record)
            if (modelClass.isInstance(mapped)) {
                mapped as S
            } else if (failOnWrongModel()) {
                error(
                    "Model ${mapped.id()} has ${mapped.javaClass.simpleName} type, " +
                        "while it should have ${modelClass.simpleName} type",
                )
            } else {
                null
            }
        }
    }
}
