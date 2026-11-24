package com.razz.eva.uow.guarded

import com.razz.eva.domain.Department
import com.razz.eva.domain.Department.OrphanedDepartment
import com.razz.eva.uow.TestPrincipal

/**
 * A proof that narrows: it allows the write on a department without a boss only, so its attempts write an
 * [OrphanedDepartment]. On a retry [answer] checks the reloaded department again. [askedAgain] counts the retries it
 * was asked about.
 */
internal class OrphanProof(
    override val target: OrphanedDepartment,
    private val answer: (Department<*>) -> OrphanedDepartment,
) : Proof<Department<*>, OrphanedDepartment>() {

    override val principal = TestPrincipal

    override val action = Write

    var askedAgain = 0
        private set

    override suspend fun checkAgain(reloaded: Department<*>): OrphanedDepartment {
        askedAgain++
        return answer(reloaded)
    }
}
