package com.razz.eva.domain

/**
 * Evidence that a mutation is happening where its registration is. [Model.raise] demands one in
 * context, so a mutator built on it is callable only inside a scope that supplies one: the change
 * block's `update(model) { }` and `add { }` supply a witness for the id type of the model they register,
 * and the test-fixtures `mutating { }` supplies one for tests. Contravariant in the id, so a witness for
 * the top id type (the fixtures' universal `mutating { }`) is a witness for every model.
 *
 * The guard is against forgetting, not against intent. The witness is per id type, not per instance:
 * inside `update(a) { }` another model of the same type can be mutated and dropped. It is an ordinary
 * value: `contextOf<Witness<X>>()` inside a scope captures it for use outside, and the internal
 * constructor is visible to Java and to a Kotlin caller that suppresses `INVISIBLE_REFERENCE`. The
 * executor's result nets are the runtime backstop for what reaches a result.
 */
class Witness<in ID : ModelId<out Comparable<*>>> internal constructor()
