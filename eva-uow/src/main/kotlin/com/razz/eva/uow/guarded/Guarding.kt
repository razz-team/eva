package com.razz.eva.uow.guarded

import com.razz.eva.domain.Model
import kotlin.reflect.KClass

/**
 * The model types that need a [Proof]. The application creates one, from the model types it issues proofs for. A
 * guarded block writes a model of these [types], or of a subtype of one, only through a [Proven]. It refuses a composed
 * child that registers such a model, and a guarded unit of work cannot list such a type in `unguardedWrites`.
 */
class Guarding(vararg types: KClass<out Model<*, *>>) {

    val types: Set<KClass<out Model<*, *>>> = types.toSet()

    /**
     * Returns true if a model of [type] can be a guarded model: [type] is a guarded type, a subtype of one, or a
     * supertype of one.
     */
    internal fun canBeGuarded(type: KClass<out Model<*, *>>): Boolean = types.any {
        it.java.isAssignableFrom(type.java) || type.java.isAssignableFrom(it.java)
    }
}
