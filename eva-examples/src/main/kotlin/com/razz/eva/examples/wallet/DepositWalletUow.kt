package com.razz.eva.examples.wallet

import com.razz.eva.examples.ServicePrincipal
import com.razz.eva.examples.wallet.DepositWalletUow.Params
import com.razz.eva.uow.ModelParam
import com.razz.eva.uow.UnitOfWork
import com.razz.eva.uow.UowParams
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.Clock

class DepositWalletUow(clock: Clock) : UnitOfWork<ServicePrincipal, Params, Wallet>(clock) {

    @Serializable
    data class Params(
        val wallet: ModelParam<Wallet.Id, @Contextual Wallet>,
        val amount: ULong,
    ) : UowParams<Params> {
        override fun serialization() = serializer()
    }

    override suspend fun tryPerform(principal: ServicePrincipal, params: Params) = changes {
        val wallet = params.wallet.model()
        val updatedWallet = wallet.deposit(params.amount)
        updatedWallet
    }
}
