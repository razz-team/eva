package com.razz.eva.examples.composition.cart

import com.razz.eva.domain.EntityState
import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.examples.composition.account.Account
import com.razz.eva.examples.composition.cart.Cart.Id
import com.razz.eva.examples.composition.cart.Cart.State.CHECKED_OUT
import com.razz.eva.examples.composition.cart.Cart.State.SHOPPING
import com.razz.eva.examples.composition.cart.CartEvent.CartCheckedOut
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.*

sealed class CartEvent : ModelEvent<Id> {

    override val modelName: String = Cart::class.java.simpleName

    data class CartCheckedOut(
        override val modelId: Id,
    ) : CartEvent()
}

class Cart(
    id: Id,
    val items: List<CartItem>,
    val paidFrom: Account.Id?,
    val state: State,
    entityState: EntityState<Id, CartEvent>,
) : Model<Id, CartEvent>(id, entityState) {

    @Serializable
    @JvmInline
    value class Id(override val id: @Contextual UUID) : ModelId<UUID> {
        constructor(id: String) : this(UUID.fromString(id))
        override fun toString() = id.toString()
        companion object {
            fun random() = Id(UUID.randomUUID())
        }
    }

    data class CartItem(
        val sku: String,
        val price: Long,
    )

    enum class State { SHOPPING, CHECKED_OUT }

    fun checkout(accountId: Account.Id): Cart {
        check(state == SHOPPING) {
            "Can checkout only shopping cart"
        }
        return existingCart(
            id = id(),
            items = this.items,
            paidFrom = accountId,
            state = CHECKED_OUT,
            entityState = raiseEvent(CartCheckedOut(modelId = id())),
        )
    }

    companion object Factory {

        fun existingCart(
            id: Id = Id.random(),
            items: List<CartItem>,
            paidFrom: Account.Id?,
            state: State,
            entityState: EntityState<Id, CartEvent>,
        ) = Cart(
            id = id,
            items = items,
            paidFrom = paidFrom,
            state = state,
            entityState = entityState,
        )
    }
}
