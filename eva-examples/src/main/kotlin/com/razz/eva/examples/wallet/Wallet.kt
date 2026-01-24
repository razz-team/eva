package com.razz.eva.examples.wallet

import com.razz.eva.domain.ModelState
import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelCreatedEvent
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.*

sealed class WalletEvent : ModelEvent<Wallet.Id> {

    override val modelName = "Wallet"

    data class Created(
        override val modelId: Wallet.Id,
        val currency: Currency,
        val amount: ULong,
        val expireAt: Instant
    ) : WalletEvent(), ModelCreatedEvent<Wallet.Id> {
        override fun integrationEvent() = buildJsonObject {
            put("currency", currency.currencyCode)
            put("amount", amount.toLong())
            put("expireAt", expireAt.epochSecond)
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

class Wallet(
    id: Id,
    val currency: Currency,
    val amount: ULong,
    val expireAt: Instant,
    modelState: ModelState<Id, WalletEvent>
) : Model<Wallet.Id, WalletEvent>(id, modelState) {

    data class Id(override val id: UUID) : ModelId<UUID>

    fun deposit(toDeposit: ULong) = Wallet(
        amount = amount - toDeposit,
        currency = currency,
        id = id(),
        expireAt = expireAt,
        modelState = raiseEvent(WalletEvent.Deposit(id(), amount, toDeposit))
    )
}
