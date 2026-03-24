package com.razz.eva.domain

import java.util.UUID

data class TxnMaterialisedView(
    val originEntity: UUID,
    val cpartyEntity: UUID,
    val transactionId: UUID,
    val value: Int,
    val currency: String,
) : UpdatableEntity() {

    data class Key(
        val transactionId: UUID,
    ) : EntityKey<TxnMaterialisedView>
}
