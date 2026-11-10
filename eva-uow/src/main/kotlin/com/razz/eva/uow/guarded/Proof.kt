package com.razz.eva.uow.guarded

import com.razz.eva.domain.Model
import com.razz.eva.domain.Principal
import com.razz.eva.uow.InstantiationContext
import java.util.concurrent.atomic.AtomicReference

/**
 * The application's record that it allowed [principal] to do [action] to [target], as [target] was at that time.
 * The application creates one where it checks permissions; eva never creates one. To use it, wrap it in a [Proven]
 * with `proven(proof, reload)` in the params of a guarded unit of work. One proof runs one unit of work: the first
 * attempt's params mark it as used, and only a retry of the same run can wrap it again. On a retry eva loads the
 * model again and calls [checkAgain].
 */
abstract class Proof<out P : Principal<*>, out A : Action, M : Model<*, *>> {

    abstract val principal: P

    abstract val action: A

    /**
     * The model the action was allowed on. It must be the stored model, with no unsaved changes. The first attempt
     * writes this model.
     */
    abstract val target: M

    /**
     * Called on a retry with [reloaded], the model as it is stored now. Check again that [principal] may do [action]
     * to it, and throw if not.
     */
    abstract suspend fun checkAgain(reloaded: M)

    private val usedBy = AtomicReference<InstantiationContext.Run?>(null)

    internal fun markUsedBy(run: InstantiationContext.Run) =
        check(usedBy.compareAndSet(null, run)) {
            "This proof for ${action.name} was already used to run a unit of work. One proof runs one unit of work: " +
                "get a new proof for another run"
        }

    internal fun isUsedBy(run: InstantiationContext.Run): Boolean = usedBy.get() === run
}
