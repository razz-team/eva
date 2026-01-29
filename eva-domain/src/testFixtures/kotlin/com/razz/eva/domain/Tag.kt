package com.razz.eva.domain

import java.util.UUID

data class Tag(
    val subjectId: UUID,
    val name: String,
    val value: String,
) : DeletableEntity() {

    init {
        require(name.isNotBlank()) { "Tag name cannot be blank" }
    }

    /**
     * Key for identifying a Tag by its subject and name.
     */
    data class Key(
        val subjectId: UUID,
        val name: String,
    ) : EntityKey<Tag>

    companion object {
        fun tag(subjectId: UUID, name: String, value: String): Tag =
            Tag(subjectId, name, value)

        fun environmentTag(subjectId: UUID, environment: String): Tag =
            Tag(subjectId, "environment", environment)

        fun priorityTag(subjectId: UUID, priority: Int): Tag =
            Tag(subjectId, "priority", priority.toString())
    }
}
