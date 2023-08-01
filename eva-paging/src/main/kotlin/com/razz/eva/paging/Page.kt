package com.razz.eva.paging

import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

typealias Offset = String

@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(with = Page.PageGenericSerializer::class)
sealed class Page<P : Comparable<P>> {
    /**
     * Return less or equal number of records
     */
    abstract val size: Size

    fun sizeValue() = size.intValue()

    abstract fun withMinSize(size: Size): Page<P>

    fun next(maxOrdering: P, offset: Offset): Next<P> = Next(maxOrdering, offset, size)

    @Serializable(with = PageGenericSerializer::class)
    data class First<P : Comparable<P>>(
        override val size: Size
    ) : Page<P>() {

        override fun withMinSize(size: Size) = copy(size = this.size.minSize(size))
    }

    @Serializable(with = PageGenericSerializer::class)
    data class Next<P : Comparable<P>>(
        /**
         * Return records with ordering field less or equal to this value
         */
        val maxOrdering: @Contextual P,
        /**
         * Return records with offset field value less than provided offset value
         */
        val offset: Offset,

        override val size: Size
    ) : Page<P>() {

        override fun withMinSize(size: Size) = copy(size = this.size.minSize(size))
    }

    companion object Factory {
        fun <P : Comparable<P>> firstPage(size: Size): First<P> = First(size)
    }

    class PageGenericSerializer<T : Comparable<T>>(
        private val orderingSerializer: KSerializer<T>,
    ) : KSerializer<Page<T>> {

        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Page") {
            element<String>("type")
            element("size", Size.serializer().descriptor)
            element("maxOrdering", orderingSerializer.descriptor)
            element<String>("offset")
        }

        override fun serialize(encoder: Encoder, value: Page<T>) {
            encoder.encodeStructure(descriptor) {
                when (value) {
                    is First -> {
                        encodeStringElement(descriptor, 0, "first")
                    }

                    is Next -> {
                        encodeStringElement(descriptor, 0, "next")
                        encodeSerializableElement(descriptor, 2, orderingSerializer, value.maxOrdering)
                        encodeStringElement(descriptor, 3, value.offset)
                    }
                }
                encodeSerializableElement(descriptor, 1, Size.serializer(), value.size)
            }
        }

        override fun deserialize(decoder: Decoder): Page<T> {
            return decoder.decodeStructure(descriptor) {
                var type: String? = null
                var size: Size? = null
                var maxOrdering: T? = null
                var offset: String? = null
                while (true) {
                    when (val index = decodeElementIndex(descriptor)) {
                        0 -> type = decodeStringElement(descriptor, 0)
                        1 -> size = Size(decodeIntElement(descriptor, 1))
                        2 -> maxOrdering = decodeSerializableElement(descriptor, 2, orderingSerializer)
                        3 -> offset = decodeStringElement(descriptor, 3)
                        CompositeDecoder.DECODE_DONE -> break
                        else -> error("Unexpected index: $index")
                    }
                }
                when (type) {
                    "first" -> First(requireNotNull(size))
                    "next" ->
                        Next(requireNotNull(maxOrdering), requireNotNull(offset), requireNotNull(size))
                    else -> error("Unexpected type: $type")
                }
            }
        }
    }
}

fun <E, P : Comparable<P>> List<E>.nextPage(
    prevPage: Page<P>,
    maxOrdering: (E) -> P,
    offset: (E) -> Offset
): Page.Next<P>? {
    return if (size < prevPage.sizeValue() || isEmpty()) {
        null
    } else {
        val lastElement = last()
        Page.Next(
            maxOrdering = maxOrdering(lastElement),
            offset = offset(lastElement),
            size = prevPage.size
        )
    }
}
