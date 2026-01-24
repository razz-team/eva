package com.razz.eva.examples.wallet

import com.razz.eva.domain.EntityState.PersistentState
import com.razz.eva.examples.schema.db.Tables.WALLET
import com.razz.eva.examples.schema.db.tables.records.WalletRecord
import com.razz.eva.paging.Page
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.eva.repository.JooqBaseModelRepository
import org.jooq.DSLContext
import java.time.Instant
import java.util.*

class WalletRepository(
    queryExecutor: QueryExecutor,
    dslContext: DSLContext,
) : WalletQueries, JooqBaseModelRepository<UUID, Wallet.Id, Wallet, WalletEvent, WalletRecord>(
    queryExecutor = queryExecutor,
    dslContext = dslContext,
    table = WALLET,
    stripNotModifiedFields = true,
) {
    override fun toRecord(model: Wallet) = WalletRecord().apply {
        currency = model.currency.currencyCode
        amount = model.amount.toLong()
        expireAt = model.expireAt
    }

    override fun fromRecord(
        record: WalletRecord,
        entityState: PersistentState<Wallet.Id, WalletEvent>,
    ) = Wallet(
        id = Wallet.Id(record.id),
        currency = Currency.getInstance(record.currency),
        amount = record.amount.toULong(),
        expireAt = record.expireAt,
        entityState = entityState,
    )

    suspend fun wallets(currency: Currency, page: Page<Instant>) = findPage(
        condition = WALLET.CURRENCY.eq(currency.currencyCode),
        page = page,
        pagingStrategy = WalletPaging,
    )
}
