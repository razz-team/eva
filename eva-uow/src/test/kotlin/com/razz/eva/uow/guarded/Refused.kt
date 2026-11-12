package com.razz.eva.uow.guarded

/** What a fixture proof throws when it refuses a reload. */
internal class Refused(message: String) : RuntimeException(message)
