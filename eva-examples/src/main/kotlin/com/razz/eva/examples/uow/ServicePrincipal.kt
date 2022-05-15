package com.razz.eva.examples.uow

import com.razz.eva.domain.Principal
import com.razz.eva.domain.Principal.Id

class ServicePrincipal(
    override val id: Id<String>,
    override val name: Principal.Name
) : Principal<String> {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ServicePrincipal

        if (id != other.id) return false
        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }

    companion object Factory {
        fun byName(name: String) = ServicePrincipal(Id(name), Principal.Name(name))
    }
}
