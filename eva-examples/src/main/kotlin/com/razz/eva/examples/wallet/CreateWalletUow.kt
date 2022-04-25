package com.razz.eva.examples.wallet

import com.razz.eva.domain.EntityState.NewState.Companion.newState
import com.razz.eva.examples.wallet.CreateWalletUow.Params
import com.razz.eva.uow.Changes
import com.razz.eva.uow.ServicePrincipal
import com.razz.eva.uow.UnitOfWork
import com.razz.eva.uow.params.UowParams
import kotlinx.serialization.Serializable
import java.time.Clock
import java.util.*

class CreateWalletUow(
    private val queries: WalletQueries,
    clock: Clock
) : UnitOfWork<ServicePrincipal, Params, Wallet>(clock) {

    @Serializable
    data class Params(val id: String, val currency: String) : UowParams<Params> {
        override fun serialization() = serializer()
    }

    override suspend fun tryPerform(principal: ServicePrincipal, params: Params): Changes<Wallet> {
        val walletId = Wallet.Id(UUID.fromString(params.id))
        val wallet = queries.find(walletId)

        return if (wallet != null) {
            noChanges(wallet)
        } else {
            val amount = ULong.MIN_VALUE
            val currency = Currency.getInstance(params.currency)
            val newWallet = Wallet(
                id = walletId,
                currency = currency,
                amount = amount,
                entityState = newState(WalletEvent.Created(walletId, currency, amount))
            )
            changes {
                add(newWallet)
            }
        }
    }
}