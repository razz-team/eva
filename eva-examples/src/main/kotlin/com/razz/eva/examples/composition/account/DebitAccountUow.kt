package com.razz.eva.examples.composition.account

import com.razz.eva.examples.ServicePrincipal
import com.razz.eva.examples.composition.account.DebitAccountUow.Params
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.composable.UnitOfWork
import com.razz.eva.uow.UowParams
import kotlinx.serialization.Serializable

class DebitAccountUow(
    private val accountQueries: (Account.Id) -> Account,
    executionContext: ExecutionContext,
) : UnitOfWork<ServicePrincipal, Params, Account.Id>(executionContext) {

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
