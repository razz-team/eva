package com.razz.eva.domain

import io.kotest.core.spec.style.FunSpec
import java.util.*
import java.util.UUID.randomUUID

@JvmInline value class IdOne(override val id: UUID) : ModelId<UUID>
@JvmInline value class IdTwo(override val id: UUID) : ModelId<UUID>

class ModelIdSpec : FunSpec({

    test("Model Ids of two different classes are not equal") {
        val sameUUID = randomUUID()
        val one = IdOne(sameUUID)
        val two = IdTwo(sameUUID)

        assert(!one.equals(two))
    }

    test("Model Ids of same class are equal") {
        val sameUUID = randomUUID()
        val one = IdOne(sameUUID)
        val sameOne = IdOne(sameUUID)

        assert(one.equals(sameOne))
    }

    test("Model Ids of same class as interfaces are equal") {
        val sameUUID = randomUUID()
        val one: ModelId<*> = IdOne(sameUUID)
        val sameOne: ModelId<*> = IdOne(sameUUID)

        assert(one.equals(sameOne))
    }

    test("Model Ids of same class have same hash code") {
        val sameUUID = randomUUID()
        val one = IdOne(sameUUID)
        val sameOne = IdOne(sameUUID)

        assert(one.hashCode() == sameOne.hashCode())
    }

    test("Model Ids of same class as interfaces have same hash code") {
        val sameUUID = randomUUID()
        val one: ModelId<*> = IdOne(sameUUID)
        val sameOne: ModelId<*> = IdOne(sameUUID)

        assert(one.hashCode() == sameOne.hashCode())
    }
})
