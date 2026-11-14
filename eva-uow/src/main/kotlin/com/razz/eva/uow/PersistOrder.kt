package com.razz.eva.uow

import com.razz.eva.domain.Model

/**
 * The order in which a unit of work's model writes reach the database. It is part of the unit of work's
 * [BaseUnitOfWork.Configuration]. Model events keep registration order whatever the order is, and entity changes
 * are written after the model changes, in registration order.
 */
sealed interface PersistOrder {

    /** As registered, one write after the other. The default. */
    data object Registration : PersistOrder

    /**
     * Sorted by [compare], stably: models it ranks equal keep their registration order. Written one after the
     * other, never batched. The models are the flattened changes (each aggregate root followed by its owned
     * models, and the models a block keeps with `notChanged`); under composition they include the parent's and
     * sibling units' rows, so a row ranked first also moves before rows a parent registered earlier. Model events
     * keep registration order.
     *
     * Rank what the order is about and return 0 for every other pair. A comparator that ranks a new owned model
     * before its new root breaks a foreign key that registration order kept. [compare] must be a total order: an
     * inconsistent one can fail the write before it starts, with "Comparison method violates its general contract".
     *
     * Implement it as an object or a data class: composition compares orders with `==`, and two lambdas are never
     * equal, even with the same body.
     */
    fun interface Custom : PersistOrder {
        fun compare(a: Model<*, *>, b: Model<*, *>): Int
    }

    /**
     * No order is promised: writes are batched per model class, updates before inserts, and the batches run in
     * parallel where the transaction manager pipelines.
     */
    data object Unspecified : PersistOrder
}

/**
 * The changes in the order they are written, for the write and for `verifyInOrder`, so the two cannot disagree.
 * [PersistOrder.Unspecified] promises no order, so its changes are listed as registered.
 */
internal fun List<ModelChange>.inWriteOrder(order: PersistOrder): List<ModelChange> = when (order) {
    PersistOrder.Registration -> this
    PersistOrder.Unspecified -> this
    is PersistOrder.Custom -> sortedWith { a, b -> order.compare(a.model, b.model) }
}
