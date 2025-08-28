package com.razz.eva.domain

interface Principal<T> {

    val id: Id<out T>
    val name: Name

    fun context(): Map<String, String> = emptyMap()

    data class Id<T>(private val id: T) {

        init {
            require(id.toString().isNotBlank()) {
                "Id must be not blank"
            }
            require(id.toString().length <= MAX_LENGTH) {
                "Id is too long"
            }
        }

        fun id() = id

        override fun toString(): String {
            return id.toString()
        }

        companion object {
            private const val MAX_LENGTH = 100
        }
    }

    @JvmInline
    value class Name(private val name: String) {

        init {
            require(name.isNotBlank()) {
                "Name must be not blank"
            }
            require(name.length <= MAX_LENGTH) {
                "Name is too long"
            }
        }

        override fun toString(): String {
            return name
        }

        companion object {
            private const val MAX_LENGTH = 100
        }
    }
}
