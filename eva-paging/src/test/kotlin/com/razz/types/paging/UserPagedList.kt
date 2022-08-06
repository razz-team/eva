package com.razz.types.paging

import com.razz.eva.paging.AbstractPagedList
import com.razz.eva.paging.Size
import java.time.Instant

class UserPagedList(list: List<User>, pageSize: Size) : AbstractPagedList<User, Instant>(list, pageSize) {
    override fun maxPivot(item: User) = item.registeredAt
    override fun offset(item: User) = item.name
}
