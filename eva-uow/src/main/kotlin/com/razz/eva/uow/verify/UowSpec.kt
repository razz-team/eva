package com.razz.eva.uow.verify

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelEvent
import com.razz.eva.uow.ChangesWithResult

infix fun <R> ChangesWithResult<R>.verifyInOrder(block: UowSpec<R>.() -> Unit) {
    val spec = UowSpec(this)
    block(spec)
    spec.verifyEnd()
}

class UowSpec<R> internal constructor(
    changes: ChangesWithResult<R>
) : UowSpecBase<R>(changes) {

    fun returnsEq(expected: R) = returns {
        check(expected == this) { "Result doesn't match" }
    }

    fun returns(verifyResult: R.() -> Unit) {
        verifyResult(verifyResult)
    }

    fun <M : Model<*, *>> addsEq(expected: M) {
        verifyAdded<M> { actual ->
            check(actual == expected) { "Got unexpected add of [$actual]" }
        }
    }

    fun <M : Model<*, *>> adds(verify: M.() -> Unit): M {
        return verifyAdded(verify)
    }

    fun <M : Model<*, *>> updatesEq(expected: M) {
        verifyUpdated<M> { actual ->
            check(actual == expected) { "Got unexpected update of [$actual]" }
        }
    }

    fun <M : Model<*, *>> updates(verify: M.() -> Unit) {
        verifyUpdated(verify)
    }

    fun <E : ModelEvent<*>> emitsEq(expected: E) {
        verifyEmitted<E> { actual ->
            check(actual == expected) { "Got unexpected emit of [$actual]" }
        }
    }

    fun <E : ModelEvent<*>> emits(verify: E.() -> Unit) {
        verifyEmitted(verify)
    }
}
