package com.razz.eva.uow.guarded

import com.razz.eva.uow.Changes

/**
 * What a guarded block returns. Sealed in this module: the one subclass is [GuardedUnitOfWork]'s own, beside its
 * `changes`, so a body that ends on `noChanges()`, a hand-built accumulator or another unit of work's `changes { }`
 * does not compile, anywhere.
 */
sealed class GuardedChanges<R> : Changes<R>()
