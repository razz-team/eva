package com.razz.eva.uow

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelId
import com.razz.eva.uow.ModelParam.Serializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = Serializer::class)
class ModelParam<MID : ModelId<out Comparable<*>>, M : Model<MID, *>> private constructor(
    private var model: M?,
    private val id: MID,
    private val modelQueries: suspend (MID) -> M
) {

    private constructor(model: M, modelQueries: suspend (MID) -> M) : this(model, model.id(), modelQueries)
    private constructor(id: MID, modelQueries: suspend (MID) -> M) : this(null, id, modelQueries)

    fun id(): MID = id
    suspend fun model(): M {
        val currentModel = model
        return if (currentModel == null) {
            val newModel = modelQueries(id)
            model = newModel
            newModel
        } else {
            currentModel
        }
    }

    class Serializer<MID : ModelId<out Comparable<*>>, M : Model<MID, *>>(
        private val idSerializer: KSerializer<MID>,
        @Suppress("unused") private val modelNoopSerializer: KSerializer<M>,
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
            val modelParam = if (attempt == 0) {
                ModelParam(model, modelQueries)
            } else {
                ModelParam(model.id(), modelQueries)
            }
            return modelParam
        }

        fun <MID : ModelId<out Comparable<*>>, M : Model<MID, *>> modelParam(
            model: M,
            modelQueries: suspend (MID) -> M,
        ): ModelParam<MID, M> {
            return ModelParam(model, modelQueries)
        }

        fun <MID : ModelId<out Comparable<*>>, M : Model<MID, *>> idModelParam(
            modelId: MID,
            modelQueries: suspend (MID) -> M,
        ): ModelParam<MID, M> {
            return ModelParam(modelId, modelQueries)
        }
    }
}
