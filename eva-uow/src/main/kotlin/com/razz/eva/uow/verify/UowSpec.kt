package com.razz.eva.uow.verify

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.domain.ModelId
import com.razz.eva.uow.Changes

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

    fun <RR : R> returnsEq(expected: RR) = returnsAs<RR> {
        check(expected == this) { "Result doesn't match" }
    }

    fun returns(verifyResult: R.() -> Unit) {
        verifyResult(verifyResult)
    }

    fun <RR : R> returnsAs(verifyResult: RR.() -> Unit) {
        verifyResultAs(verifyResult)
    }

    fun <M : Model<*, *>> addsEq(expected: M) {
        verifyAdded<M> { actual ->
            check(actual == expected) { "Got unexpected add of [$actual]" }
        }
    }

    fun <M : Model<*, *>> adds(verify: M.() -> Unit): M {
        return verifyAdded(verify)
    }

    fun <M : Model<*, *>> EqualityVerifierAware.adds(id: ModelId<*>, verify: M.() -> Unit): M {
        val verified = verifyAdded(verify)
        equalityVerifier.verify(verified.id(), id)
        return verified
    }

    fun <M : Model<*, *>> updatesEq(expected: M) {
        verifyUpdated<M> { actual ->
            check(actual == expected) { "Got unexpected update of [$actual]" }
        }
    }

    fun <M : Model<*, *>> updates(verify: M.() -> Unit): M {
        return verifyUpdated(verify)
    }

    fun <M : Model<*, *>> EqualityVerifierAware.updates(id: ModelId<*>, verify: M.() -> Unit): M {
        val verified = verifyUpdated(verify)
        equalityVerifier.verify(verified.id(), id)
        return verified
    }

    fun <E : ModelEvent<*>> emitsEq(expected: E) {
        verifyEmitted<E> { actual ->
            check(actual == expected) { "Got unexpected emit of [$actual]" }
        }
    }

    fun <E : ModelEvent<*>> emits(verify: E.() -> Unit) {
        verifyEmitted(verify)
    }

    fun <E : ModelEvent<*>> EqualityVerifierAware.emits(id: ModelId<*>, verify: E.() -> Unit) {
        val verified = verifyEmitted(verify)
        equalityVerifier.verify(verified.modelId, id)
    }
}
