package com.razz.eva.domain

import java.util.UUID

data class TxnView(
    val transactionId: UUID,
    val value: Int,
    val currency: String,
) : UpdatableEntity() {

    data class Key(
        val transactionId: UUID,
    ) : EntityKey<TxnView>
}
