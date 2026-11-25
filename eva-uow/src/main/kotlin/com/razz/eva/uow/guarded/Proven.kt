package com.razz.eva.uow.guarded

import com.razz.eva.domain.Model
import com.razz.eva.uow.InstantiationContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.encodeStructure

/**
 * A params field that carries a guarded model together with the [Proof] that allows the write. Create it in the params
 * lambda with [proven]. In the `changes` block of a guarded unit of work `params.x.model()` returns the model; only a
 * block for proofs of type [PF] has `model()`. The block then calls `update` or `notChanged` on it. On the first
 * attempt `model()` returns the model the proof was issued for. On a retry it loads the model again and asks the proof
 * to allow the action again. [M] is the type the proof's attempts write, which may be narrower than the type the
 * reload loads. The audit row holds the field as `{"action": ..., "target": ...}`, where `target` is the model id.
 */
@Serializable(with = Proven.Audit::class)
class Proven<out PF : Proof<*, *>, M : Model<*, *>> private constructor(
    // proven() read these once and checked the target: a proof's getters are not trusted to answer the same again.
    val action: String,
    private val target: M,
    private val reloadAllowed: suspend () -> M,
    private val context: InstantiationContext,
) {

    private var readModel: M? = null

    /**
     * Returns the model for this attempt and keeps it: the proof's target on the first attempt, the reloaded model on
     * a retry, after the proof allowed it again.
     */
    internal suspend fun read(): M {
        readModel?.let { return it }
        val model = if (context.attempt == 0) target else reloadAllowed()
        readModel = model
        return model
    }

    /** Returns the model [read] returned in this attempt, or null if the attempt did not read it. */
    internal fun readOrNull(): M? = readModel

    /** Returns true if [attempt] is the attempt whose params lambda created this Proven. */
    internal fun belongsTo(attempt: InstantiationContext?): Boolean = attempt === context

    val targetId: String = target.id().stringValue()

    companion object {

        /**
         * Wraps [proof] in a [Proven] for the params of this attempt. Call it in the params lambda of
         * `UnitOfWorkExecutor.execute`, once per proof. [reload] loads the proof's target again as it is stored; eva
         * calls it on a retry only, then asks the proof with `checkAgain`. Throws if the proof was issued for another
         * principal than the one the unit of work runs under; if the proof's target is new or has unsaved changes; if
         * the proof already ran another unit of work (a retry of the same run may wrap it again); or if it is called
         * in the params of a child run with `execute` inside a block.
         */
        fun <PF, L, M> InstantiationContext.proven(
            proof: PF,
            reload: suspend () -> L,
        ): Proven<PF, M> where PF : Proof<L, M>, L : Model<*, *>, M : L {
            val target = proof.target
            val action = proof.action.name
            check(this is InstantiationContext.External) {
                "proven(...) was called in the params of a composed child. A child takes no proof: call " +
                    "proven(...) in the params lambda of the unit of work the caller runs, and write the guarded " +
                    "model there"
            }
            check(proof.principal == principal) {
                "The proof for $action on ${target.id().stringValue()} was issued for another " +
                    "principal than the one this unit of work runs under. Run it under the proof's principal"
            }
            require(target.isPersisted()) {
                "The proof for $action is on ${target.id().stringValue()}, which is new or has " +
                    "unsaved changes. Get the proof on the model as it was loaded"
            }
            if (attempt == 0) {
                proof.markUsedBy(run, action)
            } else {
                check(proof.isUsedBy(run)) {
                    "The proof for $action was not wrapped with proven(...) in the first attempt " +
                        "of this run. Wrap the same proofs in every attempt. In a spec, run a retry with " +
                        "`{ ctx -> MyUow(ctx, ...) }.retry(principal) { params }`"
                }
            }
            return Proven(action, target, { allowedReload(proof, action, target, reload) }, this)
        }

        // Refuses a reload of another model or one with unsaved changes before the proof is asked, then refuses an
        // answer other than the reloaded instance, so the attempt writes the model eva loaded, at its stored version.
        private suspend fun <L : Model<*, *>, M : L> allowedReload(
            proof: Proof<L, M>,
            action: String,
            target: M,
            reload: suspend () -> L,
        ): M {
            val loaded = reload()
            check(loaded.id() == target.id()) {
                "The reload of a Proven returned ${loaded.id().stringValue()}, and its proof is for " +
                    "${target.id().stringValue()}. The reload must load the proof's target by its id"
            }
            check(loaded.isPersisted()) {
                "The reload of a Proven returned ${loaded.id().stringValue()}, which is new or has unsaved changes. " +
                    "The reload must return the model as it is stored"
            }
            val allowed = proof.checkAgain(loaded)
            check(allowed === loaded) {
                "checkAgain of the proof for $action returned another instance than the model eva " +
                    "reloaded for the retry, ${loaded.id().stringValue()}. Return the reloaded model itself"
            }
            return allowed
        }

        /** A composed child's params take no proof, so a call here is a compile error. */
        @Deprecated(
            "A composed child's params take no proof. Call proven(...) in the params lambda of the unit of work " +
                "the caller runs, and write the guarded model there.",
            level = DeprecationLevel.ERROR,
        )
        @Suppress("UnusedParameter")
        fun <PF, L, M> InstantiationContext.Internal.proven(
            proof: PF,
            reload: suspend () -> L,
        ): Nothing where PF : Proof<L, M>, L : Model<*, *>, M : L =
            error("proven(...) was called in the params of a composed child")
    }

    /**
     * Writes the audit form of a [Proven]. It cannot decode, like every params serializer. It is an `object`, so a
     * `Proven<PF, M>` field needs no serializer for `PF` or `M`.
     */
    object Audit : KSerializer<Proven<*, *>> {

        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("com.razz.eva.uow.guarded.Proven") {
            element<String>("action")
            element<String>("target")
        }

        override fun serialize(encoder: Encoder, value: Proven<*, *>) = encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.action)
            encodeStringElement(descriptor, 1, value.targetId)
        }

        override fun deserialize(decoder: Decoder): Proven<*, *> =
            error("A Proven cannot be decoded: the application issues a proof, and it is never read from stored " +
                "params")
    }
}
