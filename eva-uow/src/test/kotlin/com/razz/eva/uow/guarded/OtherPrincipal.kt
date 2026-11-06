package com.razz.eva.uow.guarded

import com.razz.eva.domain.Principal

/** A principal a proof was not minted for: TestPrincipal's id under another name, so only equality tells them apart. */
internal object OtherPrincipal : Principal<String> {
    override val id = Principal.Id("THIS_IS_SINGLETON")
    override val name = Principal.Name("OTHER_PRINCIPAL")
}
