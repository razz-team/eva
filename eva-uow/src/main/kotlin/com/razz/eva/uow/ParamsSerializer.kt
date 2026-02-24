package com.razz.eva.uow

interface ParamsSerializer {
    fun <PARAMS : UowParams<PARAMS>> serialize(params: PARAMS): String
}
