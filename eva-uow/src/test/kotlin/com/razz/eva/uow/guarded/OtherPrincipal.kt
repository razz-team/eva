package com.razz.eva.uow.guarded

import com.razz.eva.domain.Principal

/** A principal a proof was not minted for. */
internal object OtherPrincipal : Principal<String> {
    override val id = Principal.Id("SOMEBODY_ELSE")
    override val name = Principal.Name("OTHER_PRINCIPAL")
}
