package com.razz.eva.examples.wallet

import com.razz.eva.domain.EntityState.NewState.Companion.newState
import com.razz.eva.examples.wallet.CreateWalletUow.Params
import com.razz.eva.persistence.PersistenceException
import com.razz.eva.persistence.PersistenceException.ModelRecordConstraintViolationException
import com.razz.eva.persistence.PersistenceException.UniqueModelRecordViolationException
import com.razz.eva.uow.Changes
import com.razz.eva.uow.ServicePrincipal
import com.razz.eva.uow.UnitOfWork
import com.razz.eva.uow.params.UowParams
import kotlinx.serialization.Serializable
import java.time.Clock
import java.time.Duration
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
            val expireAt = clock.instant().plus(timeToExpire)
            val newWallet = Wallet(
                id = walletId,
                currency = currency,
                amount = amount,
                expireAt = expireAt,
                entityState = newState(WalletEvent.Created(walletId, currency, amount, expireAt))
            )
            changes {
                add(newWallet)
            }
        }
    }

    override suspend fun onFailure(params: Params, ex: PersistenceException): Wallet = when (ex) {
        is UniqueModelRecordViolationException -> checkNotNull(queries.find(Wallet.Id(UUID.fromString(params.id))))
        is ModelRecordConstraintViolationException -> throw IllegalArgumentException("${params.currency} is invalid")
        else -> throw ex
    }

    companion object {
        private val timeToExpire = Duration.ofDays(600)
    }
}
