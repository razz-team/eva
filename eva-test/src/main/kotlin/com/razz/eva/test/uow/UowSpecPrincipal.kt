package com.razz.eva.test.uow

import com.razz.eva.domain.Principal
import com.razz.eva.domain.Principal.Id

internal object UowSpecPrincipal : Principal<String> {
    override val id: Id<String> = Id("UowSpecPrincipal")
    override val name: Principal.Name = Principal.Name("UowSpecPrincipal")
}
