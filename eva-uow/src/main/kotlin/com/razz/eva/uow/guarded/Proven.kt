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
 * A model a guarded block writes under a proof of type [PF], carried in the unit of work's params and bound there
 * with [proven]. The block reads it with `model()`, which only a guarded block over [PF] has, and writes it with
 * `update` or holds it with `notChanged`. The first attempt keeps the model the proof was decided on; a retry loads it
 * again through the reload and the proof decides again.
 *
 * Writes into the audit row as `{"action": ..., "target": ...}`, under the field the params give it.
 */
@Serializable(with = Proven.Audit::class)
class Proven<out PF : Proof<*, *, *>, M : Model<*, *>> private constructor(
    private val proof: Proof<*, *, M>,
    private val firstAttempt: Boolean,
    private val reload: suspend () -> M,
    private val run: InstantiationContext.Run,
) {

    private var decided: M? = null

    /** The model this attempt writes, decided once: the proof's own on the first attempt, the reload's on a retry. */
    internal suspend fun decided(): M {
        decided?.let { return it }
        val model = if (firstAttempt) proof.target else reloaded()
        decided = model
        return model
    }

    /** The model [decided] answered in this attempt, or null before the attempt read it. */
    internal fun decidedOrNull(): M? = decided

    private suspend fun reloaded(): M {
        val model = reload()
        check(model.id() == proof.target.id()) {
            "The reload of a Proven returned ${model.id().stringValue()}, and its proof is for " +
                "${proof.target.id().stringValue()}. The reload must load the proof's target by its id"
        }
        proof.decideAgain(model)
        return model
    }

    /** Returns true if [context] is an attempt of the run that bound this, under the proof's principal. */
    internal fun isBoundIn(context: InstantiationContext?): Boolean =
        context != null && context.run === run && context.principal == proof.principal

    val action: String get() = proof.action.name

    val targetId: String get() = proof.target.id().stringValue()

    companion object {

        /**
         * Binds [proof] to this attempt's params, for the principal they are built for. The first attempt spends it,
         * so one proof binds one unit of work; a retry of the run that spent it binds it again. [PF] is the minter's
         * own proof type: a guarded unit of work reads only the proofs of the type it declares. A composed child's
         * params bind none: only the unit of work a caller runs reads a proof. A proof binds only a stored target.
         */
        fun <PF : Proof<*, *, M>, M : Model<*, *>> InstantiationContext.proven(
            proof: PF,
            reload: suspend () -> M,
        ): Proven<PF, M> {
            check(this !is InstantiationContext.Internal) {
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
            val firstAttempt = attempt == 0
            if (firstAttempt) {
                proof.spend(run)
            } else {
                check(proof.isSpentBy(run)) {
                    "The proof for ${proof.action.name} was not bound in the first attempt of this run. Bind the " +
                        "same proofs in every attempt. In a spec, build the first attempt's params before the retry's"
                }
            }
            return Proven(proof, firstAttempt, reload, run)
        }

        /** A composed child's params take no proof, so this call does not compile. */
        @Deprecated(
            "A composed child's params take no proof. Call proven(...) in the params lambda of the unit of work " +
                "the caller runs, and write the guarded model there.",
            level = DeprecationLevel.ERROR,
        )
        @Suppress("UnusedParameter")
        fun <PF : Proof<*, *, M>, M : Model<*, *>> InstantiationContext.Internal.proven(
            proof: PF,
            reload: suspend () -> M,
        ): Nothing = error("proven(...) was called in the params of a composed child")
    }

    /** Encode-only, as every params serializer is. An object: a params field needs no serializer per type argument. */
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
            error("A Proven cannot be decoded: the application issues a proof, and it is never read from stored params")
    }
}
