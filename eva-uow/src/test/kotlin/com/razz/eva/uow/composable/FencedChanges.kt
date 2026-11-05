package com.razz.eva.uow.composable

import com.razz.eva.uow.Changes
import com.razz.eva.uow.WrappedChanges

/** What a [FencedUow]'s block returns: sealed, so only the one subclass beside that block builds it. */
internal sealed class FencedChanges<R>(changes: Changes<R>) : WrappedChanges<R>(changes)
