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
 * One more model a guarded block writes, under a proof of its own, carried in the unit of work's params and bound
 * there with [proven]. The block reads it with `model()`, which only a guarded block has, and writes it with `update`.
 * The read follows the retry rule as [Guarded]'s target does: the first attempt keeps the model the proof was decided
 * on and spends the proof, a retry loads it again through [reload] and the proof decides again.
 *
 * Writes into the audit row as `{"action": ..., "target": ...}`, under the field the params give it.
 */
@Serializable(with = Proven.Audit::class)
class Proven<PF, M> private constructor(
    internal val proof: PF,
    private val firstAttempt: Boolean,
    private val reload: suspend () -> M,
) where PF : Proof<*, *, M>, M : Model<*, *> {

    private var loaded: M? = null

    /** The model this attempt writes, decided: the proof's own on the first attempt, the reload's on a retry. */
    internal suspend fun decided(): M {
        loaded?.let { return it }
        val model = if (firstAttempt) lent() else reloaded()
        loaded = model
        return model
    }

    private fun lent(): M {
        proof.lend()
        return proof.target
    }

    private suspend fun reloaded(): M {
        check(proof.lent) { "A proof for ${proof.action.name} is decided again only after an attempt lent it" }
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

        /** Binds [proof] to this attempt, as [Guarded.guarded] binds the target: written in the params block. */
        fun <PF, M> InstantiationContext.proven(proof: PF, reload: suspend () -> M): Proven<PF, M>
            where PF : Proof<*, *, M>, M : Model<*, *> = Proven(proof, attempt == 0, reload)
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
