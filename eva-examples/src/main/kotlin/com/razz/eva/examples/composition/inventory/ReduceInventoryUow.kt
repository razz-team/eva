package com.razz.eva.examples.composition.inventory

import com.razz.eva.examples.ServicePrincipal
import com.razz.eva.examples.composition.uow.CustomUnitOfWork
import com.razz.eva.examples.composition.inventory.Inventory.InventoryItem
import com.razz.eva.examples.composition.inventory.ReduceInventoryUow.Params
import com.razz.eva.examples.composition.uow.CustomChangesDsl
import com.razz.eva.uow.UowParams
import kotlinx.serialization.Serializable
import java.time.Clock

class ReduceInventoryUow(
    private val inventoryQueries: (Inventory.Id) -> Inventory,
    clock: Clock,
    head: CustomChangesDsl,
) : CustomUnitOfWork<ServicePrincipal, Params, Inventory.Id>(clock, head) {

    @Serializable
    data class Params(
        val inventoryId: Inventory.Id,
        val items: Map<InventoryItem, Long>
    ) : UowParams<Params> {
        override fun serialization() = serializer()
    }

    override suspend fun tryPerform(principal: ServicePrincipal, params: Params) = changes {
        val inventory = inventoryQueries(params.inventoryId)
        update(inventory.checkout(params.items)).id()
    }
}
