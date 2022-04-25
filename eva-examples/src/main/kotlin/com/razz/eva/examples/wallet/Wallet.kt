package com.razz.eva.examples.wallet

import com.razz.eva.domain.EntityState
import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelCreatedEvent
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.*

sealed class WalletEvent : ModelEvent<Wallet.Id> {

    override val modelName = "Wallet"

    data class Created(
        override val modelId: Wallet.Id,
        val currency: Currency,
        val amount: ULong
    ) : WalletEvent(), ModelCreatedEvent<Wallet.Id> {
        override fun integrationEvent() = buildJsonObject {
            put("currency", currency.currencyCode)
            put("amount", amount.toLong())
        }
    }

    data class Deposit(
        override val modelId: Wallet.Id,
        val walletAmount: ULong,
        val depositAmount: ULong
    ) : WalletEvent(), ModelCreatedEvent<Wallet.Id> {
        override fun integrationEvent() = buildJsonObject {
            put("walletAmount", walletAmount.toLong())
            put("depositAmount", depositAmount.toLong())
        }
    }
}

class Wallet constructor(
    id: Id,
    val currency: Currency,
    val amount: ULong,
    entityState: EntityState<Id, WalletEvent>
) : Model<Wallet.Id, WalletEvent>(id, entityState) {

    data class Id(override val id: UUID) : ModelId<UUID>

    fun deposit(toDeposit: ULong) = Wallet(
        amount = amount - toDeposit,
        currency = currency,
        id = id(),
        entityState = entityState()
            .raiseEvent(WalletEvent.Deposit(id(), amount, toDeposit))
    )
}
