package com.razz.eva.examples.composition

import com.razz.eva.examples.ServicePrincipal
import com.razz.eva.examples.composition.CheckoutUow.Params
import com.razz.eva.examples.composition.account.Account
import com.razz.eva.examples.composition.account.DebitAccountUow
import com.razz.eva.examples.composition.cart.Cart
import com.razz.eva.examples.composition.inventory.Inventory
import com.razz.eva.examples.composition.inventory.Inventory.InventoryItem
import com.razz.eva.examples.composition.inventory.ReduceInventoryUow
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.composable.UnitOfWork
import com.razz.eva.uow.UowParams
import kotlinx.serialization.Serializable

class CheckoutUow(
    private val cartQueries: (Cart.Id) -> Cart,
    private val accountQueries: (Account.Id) -> Account,
    private val inventoryQueries: (Inventory.Id) -> Inventory,
    private val executionContext: ExecutionContext,
) : UnitOfWork<ServicePrincipal, Params, Cart.Id>(executionContext) {

    @Serializable
    data class Params(
        val cartId: Cart.Id,
        val accountId: Account.Id,
        val inventoryId: Inventory.Id,
    ) : UowParams<Params> {
        override fun serialization() = serializer()
    }

    override suspend fun tryPerform(principal: ServicePrincipal, params: Params) = changes {
        val cart = cartQueries(params.cartId)
        var totalAmount = 0L
        val items = mutableMapOf<InventoryItem, Long>()
        cart.items.forEach { item ->
            totalAmount += item.price
            items.compute(InventoryItem(item.sku)) { _, v ->
                if (v == null) 1
                else v + 1
            }
        }
        val accountId = execute(DebitAccountUow(accountQueries, executionContext), principal) {
            DebitAccountUow.Params(params.accountId, totalAmount)
        }
        execute(ReduceInventoryUow(inventoryQueries, executionContext), principal) {
            ReduceInventoryUow.Params(params.inventoryId, items)
        }
        update(cart.checkout(accountId)).id()
    }
}
