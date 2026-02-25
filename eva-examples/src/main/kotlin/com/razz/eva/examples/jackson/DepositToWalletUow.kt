package com.razz.eva.examples.jackson

import com.razz.eva.examples.ServicePrincipal
import com.razz.eva.examples.jackson.DepositToWalletUow.Params
import com.razz.eva.examples.wallet.Wallet
import com.razz.eva.examples.wallet.WalletQueries
import com.razz.eva.uow.Changes
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.UnitOfWork
import com.razz.eva.uow.UowParams
import java.util.UUID

class DepositToWalletUow(
    private val queries: WalletQueries,
    executionContext: ExecutionContext,
) : UnitOfWork<ServicePrincipal, Params, Wallet>(executionContext) {

    data class Params(
        val walletId: String,
        val amount: Long,
    ) : UowParams<Params>

    override suspend fun tryPerform(principal: ServicePrincipal, params: Params): Changes<Wallet> {
        val walletId = Wallet.Id(UUID.fromString(params.walletId))
        val wallet = checkNotNull(queries.find(walletId)) { "Wallet ${params.walletId} not found" }
        val updated = wallet.deposit(params.amount.toULong())
        return changes {
            update(updated)
        }
    }
}
