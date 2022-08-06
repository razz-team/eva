package com.razz.eva.examples.wallet

import com.razz.eva.examples.schema.db.Tables.WALLET
import com.razz.eva.examples.schema.db.tables.records.WalletRecord
import com.razz.eva.paging.ModelOffset
import com.razz.eva.repository.PagingStrategy
import java.time.Instant
import java.util.*

object WalletPaging : PagingStrategy<UUID, Wallet.Id, Wallet, Wallet, Instant, WalletRecord>(Wallet::class) {

    override fun tableOrdering() = WALLET.EXPIRE_AT

    override fun tableId() = WALLET.ID

    override fun tableOffset(modelOffset: ModelOffset) = UUID.fromString(modelOffset)

    override fun modelOrdering(model: Wallet) = model.expireAt

    override fun modelOffset(model: Wallet) = model.id().stringValue()
}
