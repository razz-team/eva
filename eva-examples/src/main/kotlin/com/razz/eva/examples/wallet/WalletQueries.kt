package com.razz.eva.examples.wallet

interface WalletQueries {
    suspend fun find(id: Wallet.Id): Wallet?
}
