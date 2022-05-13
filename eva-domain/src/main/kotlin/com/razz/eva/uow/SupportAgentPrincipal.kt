package com.razz.eva.uow

import com.razz.eva.uow.Principal.Id
import com.razz.eva.uow.Principal.Name

class SupportAgentPrincipal(
    override val id: Id<String>
) : Principal<String> {

    override val name = Name("SupportAgent")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SupportAgentPrincipal

        if (id != other.id) return false
        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }
}
