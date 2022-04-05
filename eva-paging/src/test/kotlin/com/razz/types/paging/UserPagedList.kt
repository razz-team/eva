package com.razz.types.paging

import com.razz.eva.paging.AbstractPagedList
import com.razz.eva.paging.Size

class UserPagedList(list: List<User>, pageSize: Size) : AbstractPagedList<User>(list, pageSize) {
    override fun maxTimestamp(item: User) = item.registeredAt
    override fun offset(item: User) = item.name
}
