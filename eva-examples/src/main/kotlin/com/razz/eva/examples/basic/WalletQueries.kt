package com.razz.eva.examples.basic

interface WalletQueries {
    suspend fun find(id: Wallet.Id): Wallet?
}