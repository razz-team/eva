package com.razz.eva.repository

import com.razz.eva.uow.Principal
import com.razz.eva.uow.Principal.Id
import com.razz.eva.uow.Principal.Name

object TestPrincipal : Principal<String> {

    override val id = Id("THIS_IS_SINGLETON")

    override val name = Name("TEST_PRINCIPAL")
}
