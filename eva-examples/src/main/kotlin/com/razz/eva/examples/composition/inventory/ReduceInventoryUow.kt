package com.razz.eva.examples.composition.inventory

import com.razz.eva.examples.ServicePrincipal
import com.razz.eva.examples.composition.inventory.Inventory.InventoryItem
import com.razz.eva.examples.composition.inventory.ReduceInventoryUow.Params
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.composable.UnitOfWork
import com.razz.eva.uow.UowParams
import kotlinx.serialization.Serializable

class ReduceInventoryUow(
    private val inventoryQueries: (Inventory.Id) -> Inventory,
    executionContext: ExecutionContext,
) : UnitOfWork<ServicePrincipal, Params, Inventory.Id>(executionContext) {

    @Serializable
    data class Params(
        val inventoryId: Inventory.Id,
        val items: Map<InventoryItem, Long>,
    ) : UowParams<Params> {
        override fun serialization() = serializer()
    }

    override suspend fun tryPerform(principal: ServicePrincipal, params: Params) = changes {
        val inventory = inventoryQueries(params.inventoryId)
        update(inventory.checkout(params.items)).id()
    }
}
