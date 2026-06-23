package com.razz.eva.uow

import com.razz.eva.domain.Identifiable
import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelId
import com.razz.eva.uow.ModelParam.Serializer
import java.time.Duration
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = Serializer::class)
class ModelParam<MID : ModelId<out Comparable<*>>, M : Model<MID, *>> private constructor(
    private var model: M?,
    private val id: MID,
    private val staleAfter: Duration?,
    private val modelQueries: suspend (MID) -> M,
) : Identifiable<MID> {

    private constructor(model: M, staleAfter: Duration?, modelQueries: suspend (MID) -> M) :
        this(model, model.id(), staleAfter, modelQueries)
    private constructor(id: MID, modelQueries: suspend (MID) -> M) : this(null, id, null, modelQueries)

    override fun id(): MID = id
    suspend fun model(): M {
        val currentModel = model
        return if (currentModel == null || isStale(currentModel)) {
            val newModel = modelQueries(id)
            model = newModel
            newModel
        } else {
            currentModel
        }
    }

    private fun isStale(heldModel: M): Boolean {
        val staleAfterNanos = staleAfter?.toNanos() ?: return false
        val heldNanos = heldModel.heldNanos() ?: return false
        return heldNanos > staleAfterNanos
    }

    class Serializer<MID : ModelId<out Comparable<*>>>(
        private val idSerializer: KSerializer<MID>,
        @Suppress("unused") private val modelNoopSerializer: KSerializer<Nothing>,
    ) : KSerializer<ModelParam<MID, *>> {
        override val descriptor: SerialDescriptor = idSerializer.descriptor
        override fun serialize(encoder: Encoder, value: ModelParam<MID, *>) =
            idSerializer.serialize(encoder, value.id)
        override fun deserialize(decoder: Decoder) = ModelParam(idSerializer.deserialize(decoder)) { TODO("lel") }
    }

    companion object Factory {

        fun <MID : ModelId<out Comparable<*>>, M : Model<MID, *>> InstantiationContext.modelParam(
            model: M,
            modelQueries: suspend (MID) -> M,
        ): ModelParam<MID, M> {
            return modelParam(model, null, modelQueries)
        }

        fun <MID : ModelId<out Comparable<*>>, M : Model<MID, *>> InstantiationContext.modelParam(
            model: M,
            staleAfter: Duration?,
            modelQueries: suspend (MID) -> M,
        ): ModelParam<MID, M> {
            return if (attempt == 0) {
                ModelParam(model, staleAfter, modelQueries)
            } else {
                ModelParam(model.id(), modelQueries)
            }
        }

        fun <MID : ModelId<out Comparable<*>>, M : Model<MID, *>> InstantiationContext.Internal.constantModelParam(
            model: M,
        ): ModelParam<MID, M> {
            return ModelParam(model, null) { model }
        }

        fun <MID : ModelId<out Comparable<*>>, M : Model<MID, *>> idModelParam(
            modelId: MID,
            modelQueries: suspend (MID) -> M,
        ): ModelParam<MID, M> {
            return ModelParam(modelId, modelQueries)
        }
    }
}
