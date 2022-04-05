package com.razz.eva.paging

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

typealias ModelOffset = String

@Serializable
sealed class TimestampPage {
    /**
     * Return less or equal number of records
     */
    abstract val size: Size

    fun sizeValue() = size.intValue()

    abstract fun withMinSize(size: Size): TimestampPage

    fun next(maxTimestamp: Instant, modelIdOffset: ModelOffset) = Next(maxTimestamp, modelIdOffset, size)

    @Serializable
    @SerialName("first")
    data class First(
        override val size: Size
    ) : TimestampPage() {

        override fun withMinSize(size: Size) = copy(size = this.size.minSize(size))
    }

    @Serializable
    @SerialName("next")
    data class Next(
        /**
         * Return records with timestamp less or equal to this value
         */
        val maxTimestamp: @Contextual Instant,
        /**
         * Return records with id less than provided id
         */
        val modelIdOffset: ModelOffset,

        override val size: Size
    ) : TimestampPage() {

        override fun withMinSize(size: Size) = copy(size = this.size.minSize(size))
    }
}

fun <E> List<E>.nextPage(
    prevPage: TimestampPage,
    maxTimestamp: (E) -> Instant,
    offset: (E) -> ModelOffset
): TimestampPage.Next? {
    return if (size < prevPage.sizeValue() || size == 0) {
        null
    } else {
        val lastElement = last()
        TimestampPage.Next(
            maxTimestamp = maxTimestamp(lastElement),
            modelIdOffset = offset(lastElement),
            size = prevPage.size
        )
    }
}
