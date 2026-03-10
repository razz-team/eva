package com.razz.eva.domain

abstract class Aggregate<ID : ModelId<out Comparable<*>>, E : ModelEvent<ID>>(
    id: ID,
    modelState: ModelState<ID, E>,
    private val ownedModels: List<Model<*, *>> = listOf(),
) : Model<ID, E>(id, modelState) {
    internal fun ownedModels(): List<Model<*, *>> = ownedModels
}
