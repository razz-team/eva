package com.razz.eva.uow.guarded

/** What the fixtures' decider throws for a deny. */
internal class Refused(message: String) : RuntimeException(message)
