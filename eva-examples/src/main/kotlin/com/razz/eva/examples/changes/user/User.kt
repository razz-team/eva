package com.razz.eva.examples.changes.user

import com.razz.eva.domain.EntityState
import com.razz.eva.domain.EntityState.NewState.Companion.newState
import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelCreatedEvent
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.examples.changes.user.User.Address
import com.razz.eva.examples.changes.user.User.FirstName
import com.razz.eva.examples.changes.user.User.Id
import com.razz.eva.examples.changes.user.User.LastName
import com.razz.eva.examples.changes.user.UserEvent.UserAddressChanged
import com.razz.eva.examples.changes.user.UserEvent.UserCreated
import com.razz.eva.examples.changes.user.UserEvent.UserFirstNameChanged
import com.razz.eva.examples.changes.user.UserEvent.UserLastNameChanged
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.*

sealed class UserEvent : ModelEvent<Id> {

    override val modelName: String = User::class.java.simpleName

    data class UserCreated(
        override val modelId: Id,
        val firstName: FirstName?,
        val lastName: LastName?,
        val address: Address?,
    ) : UserEvent(), ModelCreatedEvent<Id> {

        override fun integrationEvent() = buildJsonObject {
            put("firstName", firstName?.stringValue())
            put("lastName", lastName?.stringValue())
            put("address", address?.stringValue())
        }
    }

    data class UserFirstNameChanged(
        override val modelId: Id,
        val oldFirstName: FirstName?,
        val newFirstName: FirstName?,
    ) : UserEvent(), ModelCreatedEvent<Id> {

        override fun integrationEvent() = buildJsonObject {
            put("oldFirstName", oldFirstName?.stringValue())
            put("newFirstName", newFirstName?.stringValue())
        }
    }

    data class UserLastNameChanged(
        override val modelId: Id,
        val oldLastName: LastName?,
        val newLastName: LastName?,
    ) : UserEvent(), ModelCreatedEvent<Id> {

        override fun integrationEvent() = buildJsonObject {
            put("oldLastName", oldLastName?.stringValue())
            put("newLastName", newLastName?.stringValue())
        }
    }

    data class UserAddressChanged(
        override val modelId: Id,
        val oldAddress: Address?,
        val newAddress: Address?,
    ) : UserEvent(), ModelCreatedEvent<Id> {

        override fun integrationEvent() = buildJsonObject {
            put("oldAddress", oldAddress?.stringValue())
            put("newAddress", newAddress?.stringValue())
        }
    }
}

class User(
    id: Id,
    val firstName: FirstName?,
    val lastName: LastName?,
    val address: Address?,
    entityState: EntityState<Id, UserEvent>
) : Model<Id, UserEvent>(id, entityState) {

    @Serializable
    @JvmInline
    value class Id(override val id: @Contextual UUID) : ModelId<UUID> {
        constructor(id: String) : this(UUID.fromString(id))
        override fun toString() = id.toString()
        companion object {
            fun random() = Id(UUID.randomUUID())
        }
    }

    @Serializable
    @JvmInline
    value class FirstName(private val value: String) {
        override fun toString() = value
        fun stringValue(): String = value
    }

    @Serializable
    @JvmInline
    value class LastName(private val value: String) {
        override fun toString() = value
        fun stringValue(): String = value
    }

    @Serializable
    @JvmInline
    value class Address(private val value: String) {
        override fun toString() = value
        fun stringValue(): String = value
    }

    fun updateFirstName(firstName: FirstName?) = if (this.firstName == firstName) null else existingUser(
        id = id(),
        firstName = firstName,
        lastName = lastName,
        address = address,
        entityState = entityState().raiseEvent(
            UserFirstNameChanged(
                modelId = id(),
                oldFirstName = this.firstName,
                newFirstName = firstName,
            )
        )
    )

    fun updateLastName(lastName: LastName?) = if (this.lastName == lastName) null else existingUser(
        id = id(),
        firstName = firstName,
        lastName = lastName,
        address = address,
        entityState = entityState().raiseEvent(
            UserLastNameChanged(
                modelId = id(),
                oldLastName = this.lastName,
                newLastName = lastName,
            )
        )
    )

    fun updateAddress(address: Address?) = if (this.address == address) null else existingUser(
        id = id(),
        firstName = firstName,
        lastName = lastName,
        address = address,
        entityState = entityState().raiseEvent(
            UserAddressChanged(
                modelId = id(),
                oldAddress = this.address,
                newAddress = address
            )
        )
    )

    companion object Factory {

        fun newUser(
            id: Id = Id.random(),
            firstName: FirstName? = null,
            lastName: LastName? = null,
            address: Address? = null
        ) = User(
            id = id,
            firstName = firstName,
            lastName = lastName,
            address = address,
            entityState = newState(
                UserCreated(
                    modelId = id,
                    firstName = firstName,
                    lastName = lastName,
                    address = address,
                )
            )
        )

        fun existingUser(
            id: Id = Id.random(),
            firstName: FirstName? = null,
            lastName: LastName? = null,
            address: Address? = null,
            entityState: EntityState<Id, UserEvent>
        ) = User(
            id = id,
            firstName = firstName,
            lastName = lastName,
            address = address,
            entityState = entityState
        )
    }
}
