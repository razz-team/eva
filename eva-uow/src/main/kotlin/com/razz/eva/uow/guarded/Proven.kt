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
 * to allow the action again. The audit row holds the field as `{"action": ..., "target": ...}`, where `target` is the
 * model id.
 */
@Serializable(with = Proven.Audit::class)
class Proven<out PF : Proof<*>, M : Model<*, *>> private constructor(
    private val proof: Proof<M>,
    private val reload: suspend () -> M,
    private val context: InstantiationContext,
) {

    private var readModel: M? = null

    /**
     * Returns the model for this attempt and keeps it: the proof's target on the first attempt, the reloaded model on
     * a retry, after the proof allowed it again.
     */
    internal suspend fun read(): M {
        readModel?.let { return it }
        val model = if (context.attempt == 0) proof.target else reloaded()
        readModel = model
        return model
    }

    /** Returns the model [read] returned in this attempt, or null if the attempt did not read it. */
    internal fun readOrNull(): M? = readModel

    private suspend fun reloaded(): M {
        val model = reload()
        check(model.id() == proof.target.id()) {
            "The reload of a Proven returned ${model.id().stringValue()}, and its proof is for " +
                "${proof.target.id().stringValue()}. The reload must load the proof's target by its id"
        }
        proof.checkAgain(model)
        return model
    }

    /** Returns true if [attempt] is the attempt whose params lambda created this Proven. */
    internal fun belongsTo(attempt: InstantiationContext?): Boolean = attempt === context

    val action: String get() = proof.action.name

    val targetId: String get() = proof.target.id().stringValue()

    companion object {

        /**
         * Wraps [proof] in a [Proven] for the params of this attempt. Call it in the params lambda of
         * `UnitOfWorkExecutor.execute`, once per proof. [reload] loads the proof's target again; eva calls it on a
         * retry only. Throws if the proof was issued for another principal than the one the unit of work runs under;
         * if the proof's target is new or has unsaved changes; if the proof already ran another unit of work (a retry
         * of the same run may wrap it again); or if it is called in the params of a child run with `execute` inside a
         * block.
         */
        fun <PF : Proof<M>, M : Model<*, *>> InstantiationContext.proven(
            proof: PF,
            reload: suspend () -> M,
        ): Proven<PF, M> {
            check(this is InstantiationContext.External) {
                "proven(...) was called in the params of a composed child. A child takes no proof: call " +
                    "proven(...) in the params lambda of the unit of work the caller runs, and write the guarded " +
                    "model there"
            }
            check(proof.principal == principal) {
                "The proof for ${proof.action.name} on ${proof.target.id().stringValue()} was issued for another " +
                    "principal than the one this unit of work runs under. Run it under the proof's principal"
            }
            require(proof.target.isPersisted()) {
                "The proof for ${proof.action.name} is on ${proof.target.id().stringValue()}, which is new or has " +
                    "unsaved changes. Get the proof on the model as it was loaded"
            }
            if (attempt == 0) {
                proof.markUsedBy(run)
            } else {
                check(proof.isUsedBy(run)) {
                    "The proof for ${proof.action.name} was not wrapped with proven(...) in the first attempt " +
                        "of this run. Wrap the same proofs in every attempt. In a spec, run a retry with " +
                        "`{ ctx -> MyUow(ctx, ...) }.retry(principal) { params }`"
                }
            }
            return Proven(proof, reload, this)
        }

        /** A composed child's params take no proof, so a call here is a compile error. */
        @Deprecated(
            "A composed child's params take no proof. Call proven(...) in the params lambda of the unit of work " +
                "the caller runs, and write the guarded model there.",
            level = DeprecationLevel.ERROR,
        )
        @Suppress("UnusedParameter")
        fun <PF : Proof<M>, M : Model<*, *>> InstantiationContext.Internal.proven(
            proof: PF,
            reload: suspend () -> M,
        ): Nothing = error("proven(...) was called in the params of a composed child")
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
