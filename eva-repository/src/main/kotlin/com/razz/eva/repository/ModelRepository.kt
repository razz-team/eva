package com.razz.eva.repository

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelId

interface ModelRepository<MID : ModelId<out Comparable<*>>, M : Model<MID, *>> {

    suspend fun find(id: MID): M?

    /**
     * This method meant to be used only by
     * [com.razz.eva.uow.ModelPersisting#add]
     * sadly we don't have a way (yet) to encapsulate it
     * for test purposes use
     * @see [com.razz.eva.uow.ModelPersisting]
     */
    suspend fun <ME : M> add(context: TransactionalContext, model: ME): ME

    suspend fun <ME : M> add(context: TransactionalContext, models: List<ME>)

    /**
     * This method meant to be used only by
     * [com.razz.eva.uow.ModelPersisting#update]
     * sadly we don't have a way (yet) to encapsulate it
     * for test purposes use
     * @see [com.razz.eva.uow.ModelPersisting]
     */
    suspend fun <ME : M> update(context: TransactionalContext, model: ME): ME

    suspend fun <ME : M> update(context: TransactionalContext, models: List<ME>)
}
