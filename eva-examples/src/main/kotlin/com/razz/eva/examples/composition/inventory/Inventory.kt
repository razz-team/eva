package com.razz.eva.examples.composition.inventory

import com.razz.eva.domain.ModelState
import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.examples.composition.inventory.Inventory.Id
import com.razz.eva.examples.composition.inventory.InventoryEvent.StockReduced
import kotlin.collections.LinkedHashMap
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.*

sealed class InventoryEvent : ModelEvent<Id> {

    override val modelName: String = Inventory::class.java.simpleName

    data class StockReduced(
        override val modelId: Id,
        val items: Map<Inventory.InventoryItem, Long>,
    ) : InventoryEvent() {

        override fun integrationEvent() = buildJsonObject {
            putJsonObject("items") {
                items.forEach { item ->
                    put(item.key.sku, item.value)
                }
            }
        }
    }
}

class Inventory(
    id: Id,
    val stock: Map<InventoryItem, Long>,
    modelState: ModelState<Id, InventoryEvent>
) : Model<Id, InventoryEvent>(id, modelState) {

    @Serializable
    @JvmInline
    value class Id(override val id: @Contextual UUID) : ModelId<UUID> {
        constructor(id: String) : this(UUID.fromString(id))
        override fun toString() = id.toString()
        companion object {
            fun random() = Id(UUID.randomUUID())
        }
    }

    @Serializable
    data class InventoryItem(
        val sku: String,
    )

    fun checkout(items: Map<InventoryItem, Long>): Inventory {
        val reducedStock = LinkedHashMap(this.stock)
        items.forEach { (item, amount) ->
            check(stock.getValue(item) >= amount) {
                "Insufficient stock for ${item.sku}"
            }
            reducedStock.merge(item, amount) { old, new -> old - new }
        }
        return existingInventory(
            id = id(),
            stock = reducedStock,
            modelState = raiseEvent(StockReduced(id(), items))
        )
    }

    companion object Factory {

        fun existingInventory(
            id: Id = Id.random(),
            stock: Map<InventoryItem, Long>,
            modelState: ModelState<Id, InventoryEvent>
        ) = Inventory(
            id = id,
            stock = stock,
            modelState = modelState
        )
    }
}
