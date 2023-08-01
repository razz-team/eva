package com.razz.eva.examples.wallet

import com.razz.eva.examples.schema.db.Tables.WALLET
import com.razz.eva.examples.schema.db.tables.records.WalletRecord
import com.razz.eva.paging.Offset
import com.razz.eva.repository.ModelPagingStrategy
import java.time.Instant
import java.util.*

object WalletPaging : ModelPagingStrategy<UUID, Wallet.Id, Wallet, Wallet, Instant, WalletRecord>(Wallet::class) {

    override fun tableOrdering() = WALLET.EXPIRE_AT

    override fun tableId() = WALLET.ID

    override fun tableOffset(offset: Offset) = UUID.fromString(offset)

    override fun ordering(data: Wallet) = data.expireAt

    override fun offset(data: Wallet) = data.id().stringValue()
}
