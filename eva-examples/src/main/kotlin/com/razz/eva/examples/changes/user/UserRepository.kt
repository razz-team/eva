package com.razz.eva.examples.changes.user

import com.razz.eva.domain.ModelState.PersistentState
import com.razz.eva.examples.schema.db.Tables.USER
import com.razz.eva.examples.schema.db.tables.records.UserRecord
import com.razz.eva.examples.changes.user.User.Factory.existingUser
import com.razz.eva.examples.changes.user.User.Id
import com.razz.eva.examples.changes.user.UserQueries.UserNotFoundException
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.eva.repository.JooqBaseModelRepository
import org.jooq.DSLContext
import java.util.*

class UserRepository(
    queryExecutor: QueryExecutor,
    dslContext: DSLContext,
) : UserQueries, JooqBaseModelRepository<UUID, Id, User, UserEvent, UserRecord>(
    queryExecutor = queryExecutor,
    dslContext = dslContext,
    table = USER,
    stripNotModifiedFields = true,
) {

    override suspend fun get(id: Id) = find(id) ?: throw UserNotFoundException(id)

    override fun toRecord(model: User) = UserRecord().apply {
        model.firstName?.also { firstName = it.stringValue() }
        model.lastName?.also { lastName = it.stringValue() }
        model.address?.also { address = it.stringValue() }
    }

    override fun fromRecord(
        record: UserRecord,
        modelState: PersistentState<Id, UserEvent>,
    ) = existingUser(
        id = Id(record.id),
        firstName = record.firstName?.let(User::FirstName),
        lastName = record.lastName?.let(User::LastName),
        address = record.address?.let(User::Address),
        modelState = modelState,
    )
}
