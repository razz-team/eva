package com.razz.eva.persistence

import kotlin.coroutines.CoroutineContext

object PrimaryConnectionRequiredFlag : CoroutineContext.Element, CoroutineContext.Key<PrimaryConnectionRequiredFlag> {
    override val key: CoroutineContext.Key<PrimaryConnectionRequiredFlag> = PrimaryConnectionRequiredFlag
}
