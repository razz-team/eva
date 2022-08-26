package com.razz.eva.examples.changes.user

interface UserQueries {

    class UserNotFoundException private constructor(
        message: String,
    ) : IllegalStateException(message) {
        constructor(id: User.Id) : this("User is not found for ${id.id}")
    }

    suspend fun get(id: User.Id): User
}
