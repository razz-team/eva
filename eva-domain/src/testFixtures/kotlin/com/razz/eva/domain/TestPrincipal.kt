package com.razz.eva.domain

import com.razz.eva.domain.Principal.Id
import com.razz.eva.domain.Principal.Name

object TestPrincipal : Principal<String> {

    override val id = Id("THIS_IS_SINGLETON")

    override val name = Name("TEST_PRINCIPAL")
}
