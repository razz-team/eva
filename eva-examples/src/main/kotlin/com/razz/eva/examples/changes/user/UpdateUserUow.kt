package com.razz.eva.examples.changes.user

import com.razz.eva.examples.ServicePrincipal
import com.razz.eva.examples.changes.uow.CustomUnitOfWork as UnitOfWork
import com.razz.eva.examples.changes.user.UpdateUserUow.Params
import com.razz.eva.examples.changes.user.User.Address
import com.razz.eva.examples.changes.user.User.FirstName
import com.razz.eva.examples.changes.user.User.LastName
import com.razz.eva.uow.serialization.kotlinx.UowParams
import kotlinx.serialization.Serializable
import java.time.Clock

class UpdateUserUow(
    private val userQueries: UserQueries,
    clock: Clock
) : UnitOfWork<ServicePrincipal, Params, User>(clock) {

    @Serializable
    data class Params(
        val userId: User.Id,
        val firstName: FirstName?,
        val lastName: LastName?,
        val address: Address?,
    ) : UowParams<Params> {
        override fun serialization() = serializer()
    }

    override suspend fun tryPerform(principal: ServicePrincipal, params: Params) = changes {
        val user = userQueries.get(params.userId)
        val updatedUser = updateIfChanged(user) {
            also { updateFirstName(params.firstName) }
            also { updateLastName(params.lastName) }
            also { updateAddress(params.address) }
        } ?: user
        updatedUser
    }
}
