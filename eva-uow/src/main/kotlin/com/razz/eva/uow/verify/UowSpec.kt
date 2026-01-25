package com.razz.eva.uow.verify

import com.razz.eva.domain.CreatableEntity
import com.razz.eva.domain.DeletableEntity
import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.uow.Changes
import kotlin.jvm.java

interface EqualityVerifierAware {
    val equalityVerifier: EqualityVerifier
}

interface EqualityVerifier {
    fun <T> verify(expected: T, actual: T)
}

infix fun <R> Changes<R>.verifyInOrder(block: UowSpec<R>.() -> Unit) {
    val spec = UowSpec(this)
    block(spec)
    spec.verifyEnd()
}

class UowSpec<R> internal constructor(
    changes: Changes<R>,
) : UowSpecBase<R>(changes) {

    inline fun <reified RR : R> returnsEq(expected: RR) = returnsAs<RR> {
        check(expected == this) { "Result doesn't match got $this instead of $expected" }
    }

    fun returns(verifyResult: R.() -> Unit) {
        verifyResultInternal(verifyResult)
    }

    inline fun <reified RR : R> returnsAs(noinline verifyResult: RR.() -> Unit) {
        verifyResultAsInternal(verifyResult)
    }

    inline fun <reified T> addsEq(expected: T) {
        adds<T> {
            check(expected == this) {
                if (this is Model<*, *>) "Got unexpected add of [$this]"
                else "Got unexpected entity add of [$this]"
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T> adds(noinline verify: T.() -> Unit): T {
        return when {
            Model::class.java.isAssignableFrom(T::class.java) ->
                verifyAddedModel(verify as (Model<*, *>) -> Unit) as T
            CreatableEntity::class.java.isAssignableFrom(T::class.java) ->
                verifyAddedEntity(verify as (CreatableEntity) -> Unit) as T
            else -> throw IllegalArgumentException(
                "Type parameter must be either Model or CreatableEntity, was ${T::class}",
            )
        }
    }

    inline fun <reified M : Model<*, *>> EqualityVerifierAware.adds(
        id: ModelId<*>,
        noinline verify: M.() -> Unit,
    ): M {
        val verified = verifyAddedModel(verify)
        equalityVerifier.verify(verified.id(), id)
        return verified
    }

    inline fun <reified M : Model<*, *>> addsAndReturns(noinline verify: M.() -> Unit): M {
        verifyResultAsInternal(verify)
        return verifyAddedModel(verify)
    }

    inline fun <reified M : Model<*, *>> EqualityVerifierAware.addsAndReturns(
        id: ModelId<*>,
        noinline verify: M.() -> Unit,
    ): M {
        verifyResultAsInternal(verify)
        val verified = verifyAddedModel(verify)
        equalityVerifier.verify(verified.id(), id)
        return verified
    }

    inline fun <reified M : Model<*, *>> updatesEq(expected: M) {
        updates<M> {
            check(expected == this) { "Got unexpected update of [$this]" }
        }
    }

    inline fun <reified M : Model<*, *>> updates(noinline verify: M.() -> Unit): M {
        return verifyUpdatedModel(verify)
    }

    inline fun <reified M : Model<*, *>> EqualityVerifierAware.updates(
        id: ModelId<*>,
        noinline verify: M.() -> Unit,
    ): M {
        val verified = verifyUpdatedModel(verify)
        equalityVerifier.verify(verified.id(), id)
        return verified
    }

    inline fun <reified M : Model<*, *>> updatesAndReturns(noinline verify: M.() -> Unit): M {
        verifyResultAsInternal(verify)
        return verifyUpdatedModel(verify)
    }

    inline fun <reified M : Model<*, *>> EqualityVerifierAware.updatesAndReturns(
        id: ModelId<*>,
        noinline verify: M.() -> Unit,
    ): M {
        verifyResultAsInternal(verify)
        val verified = verifyUpdatedModel(verify)
        equalityVerifier.verify(verified.id(), id)
        return verified
    }

    inline fun <reified E : ModelEvent<*>> emitsEq(expected: E) {
        emits<E> {
            check(expected == this) { "Got unexpected emit of [$this]" }
        }
    }

    inline fun <reified E : ModelEvent<*>> emits(noinline verify: E.() -> Unit) {
        verifyEmittedEvent(verify)
    }

    inline fun <reified E : ModelEvent<*>> EqualityVerifierAware.emits(
        id: ModelId<*>,
        noinline verify: E.() -> Unit,
    ) {
        val verified = verifyEmittedEvent(verify)
        equalityVerifier.verify(verified.modelId, id)
    }

    inline fun <reified E : DeletableEntity> deletesEq(expected: E) {
        deletes<E> {
            check(expected == this) { "Got unexpected entity delete of [$this]" }
        }
    }

    inline fun <reified E : DeletableEntity> deletes(noinline verify: E.() -> Unit): E {
        return verifyDeletedEntity(verify)
    }
}
