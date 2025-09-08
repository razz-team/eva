package com.razz.eva.uow

import kotlin.reflect.KClass

class UowFactoryNotFoundException(uowClass: KClass<*>) :
    IllegalStateException("There is no configured factory to create ${uowClass.simpleName}")
