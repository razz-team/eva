package com.razz.eva.uow

import com.razz.eva.repository.ModelRepos

data class InstantiationContext internal constructor(
    internal val attempt: Int,
    val repos: ModelRepos
)
