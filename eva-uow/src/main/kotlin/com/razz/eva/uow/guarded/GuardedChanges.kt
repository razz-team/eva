package com.razz.eva.uow.guarded

import com.razz.eva.uow.Changes

/**
 * The return type of a guarded unit of work's `tryPerform`. Only the `changes { }` of [GuardedUnitOfWork] creates
 * one. A body that returns `noChanges()`, changes built by hand, or the `changes { }` of a plain unit of work is a
 * compile error.
 */
sealed class GuardedChanges<R> : Changes<R>()
