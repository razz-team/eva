package com.razz.eva.examples.changes.user

import com.razz.eva.domain.Queries

interface UserQueries : Queries<User.Id, User> {

    class UserNotFoundException private constructor(
        message: String,
    ) : IllegalStateException(message) {
        constructor(id: User.Id) : this("User is not found for ${id.id}")
    }
}
