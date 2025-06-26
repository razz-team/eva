package com.razz.eva.examples.composition.account

import com.razz.eva.examples.ServicePrincipal
import com.razz.eva.examples.composition.account.DebitAccountUow.Params
import com.razz.eva.uow.composable.UnitOfWork
import com.razz.eva.uow.UowParams
import kotlinx.serialization.Serializable
import java.time.Clock

class DebitAccountUow(
    private val accountQueries: (Account.Id) -> Account,
    clock: Clock,
) : UnitOfWork<ServicePrincipal, Params, Account.Id>(clock) {

    @Serializable
    data class Params(
        val accountId: Account.Id,
        val amount: Long,
    ) : UowParams<Params> {
        override fun serialization() = serializer()
    }

    override suspend fun tryPerform(principal: ServicePrincipal, params: Params) = changes {
        val account = accountQueries(params.accountId)
        update(account.debit(params.amount)).id()
    }
}
