package com.razz.eva.examples.composition.account

import com.razz.eva.domain.ModelState
import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.examples.composition.account.Account.Id
import com.razz.eva.examples.composition.account.AccountEvent.AccountDebited
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.*

sealed class AccountEvent : ModelEvent<Id> {

    override val modelName: String = Account::class.java.simpleName

    data class AccountDebited(
        override val modelId: Id,
        val oldBalance: Long,
        val newBalance: Long,
    ) : AccountEvent() {

        override fun integrationEvent() = buildJsonObject {
            put("oldBalance", oldBalance)
            put("newBalance", newBalance)
        }
    }
}

class Account(
    id: Id,
    val balance: Long,
    modelState: ModelState<Id, AccountEvent>,
) : Model<Id, AccountEvent>(id, modelState) {

    @Serializable
    @JvmInline
    value class Id(override val id: @Contextual UUID) : ModelId<UUID> {
        constructor(id: String) : this(UUID.fromString(id))
        override fun toString() = id.toString()
        companion object {
            fun random() = Id(UUID.randomUUID())
        }
    }

    fun debit(amount: Long) = existingAccount(
        id = id(),
        balance = this.balance - amount,
        modelState = raiseEvent(
            AccountDebited(
                modelId = id(),
                oldBalance = balance,
                newBalance = this.balance - amount,
            ),
        ),
    )

    companion object Factory {

        fun existingAccount(
            id: Id = Id.random(),
            balance: Long,
            modelState: ModelState<Id, AccountEvent>,
        ) = Account(
            id = id,
            balance = balance,
            modelState = modelState,
        )
    }
}
