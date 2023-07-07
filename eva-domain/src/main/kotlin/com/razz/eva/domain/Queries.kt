package com.razz.eva.domain

interface Queries<MID : ModelId<out Comparable<*>>, M : Model<MID, *>> : suspend (MID) -> M {

    suspend fun find(id: MID): M?

    suspend fun get(id: MID): M

    override suspend fun invoke(id: MID): M = get(id)
}
