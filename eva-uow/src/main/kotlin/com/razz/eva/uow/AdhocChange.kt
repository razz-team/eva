package com.razz.eva.uow

internal data class AdhocChange(
    val block: suspend() -> Unit,
) : Change
