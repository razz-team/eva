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
            "The reload answered ${model.id().stringValue()} for the proof's ${proof.target.id().stringValue()}"
        }
        proof.decideAgain(model)
        return model
    }

    val action: String get() = proof.action.name

    val targetId: String get() = proof.target.id().stringValue()

    companion object {

        /**
         * Binds [proof] to this attempt's params, for the principal they are built for. The first attempt spends it,
         * so one proof binds one unit of work; a retry of the run that spent it binds it again. [PF] is the minter's
         * own proof type: a guarded unit of work reads only the proofs of the type it declares. A composed child's
         * params bind none: only the unit of work a caller runs reads a proof.
         */
        fun <PF : Proof<*, *, M>, M : Model<*, *>> InstantiationContext.proven(
            proof: PF,
            reload: suspend () -> M,
        ): Proven<PF, M> {
            check(this !is InstantiationContext.Internal) {
                "A proof binds in the params a caller builds for a unit of work, not in a composed child's"
            }
            check(proof.principal == principal) {
                "A proof for ${proof.action.name} on ${proof.target.id().stringValue()} " +
                    "was minted for another principal"
            }
            val firstAttempt = attempt == 0
            if (firstAttempt) {
                proof.spend(run)
            } else {
                check(proof.isSpentBy(run)) {
                    "A proof for ${proof.action.name} binds a retry only of the run that spent it"
                }
            }
            return Proven(proof, firstAttempt, reload)
        }
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
            error("A proof is minted by the framework's decider, never read back")
    }
}
