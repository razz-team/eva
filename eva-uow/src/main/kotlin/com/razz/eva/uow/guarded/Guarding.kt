package com.razz.eva.uow.guarded

import com.razz.eva.domain.Model
import kotlin.reflect.KClass

/**
 * Which models a guarded block writes only under a [Proof]: the framework's aggregates with actions. A block refuses
 * a composed child that registers one, and a unit of work may not declare one in its `unguardedWrites`.
 */
fun interface Guarding {

    /** Whether [type] reaches a guarded model: one of them, or a supertype of one. */
    fun covers(type: KClass<out Model<*, *>>): Boolean
}
