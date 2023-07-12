package com.razz.eva.domain

interface Queries<MID : ModelId<out Comparable<*>>, M : Model<MID, *>> : suspend (MID) -> M {

    suspend fun find(id: MID): M?

    suspend fun get(id: MID): M

    override suspend fun invoke(id: MID): M = get(id)
}

fun <MID : ModelId<out Comparable<*>>, M : Model<MID, *>, Q : Queries<MID, M>> Q.pin(model: M): Queries<MID, M> {
    return object : Queries<MID, M> by this {
        override suspend fun find(id: MID): M? = if (id == model.id()) model else this@pin.find(id)
        override suspend fun get(id: MID): M = if (id == model.id()) model else this@pin.get(id)
        override suspend fun invoke(id: MID): M = if (id == model.id()) model else this@pin(id)
    }
}
