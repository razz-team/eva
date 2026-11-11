package com.razz.eva.uow

sealed class InstantiationContext(internal val attempt: Int) {

    internal class External(attempt: Int) : InstantiationContext(attempt)

    class Internal internal constructor(attempt: Int) : InstantiationContext(attempt)
}
