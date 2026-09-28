package com.razz.eva.domain

/**
 * Proof that a mutation is happening where its registration is. [Model.raise] demands one in context,
 * so a mutator built on it is callable only inside a scope that supplies one: the change block's
 * `update(model) { }` and `add(model) { }` supply a witness for the id type of the model they register,
 * and the test-fixtures `mutating { }` supplies one for tests. Contravariant in the id, so a witness for
 * the top id type is a witness for every model; the constructor is internal, so only those mints exist.
 */
class Witness<in ID : ModelId<out Comparable<*>>> internal constructor()
