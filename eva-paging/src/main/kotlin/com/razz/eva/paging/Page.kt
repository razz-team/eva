package com.razz.eva.paging

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias ModelOffset = String

@Serializable
sealed class Page<P : Comparable<P>> {
    /**
     * Return less or equal number of records
     */
    abstract val size: Size

    fun sizeValue() = size.intValue()

    abstract fun withMinSize(size: Size): Page<P>

    fun next(maxOrdering: P, modelIdOffset: ModelOffset): Next<P> = Next(maxOrdering, modelIdOffset, size)

    @Serializable
    @SerialName("first")
    data class First<P : Comparable<P>>(
        override val size: Size
    ) : Page<P>() {

        override fun withMinSize(size: Size) = copy(size = this.size.minSize(size))
    }

    @Serializable
    @SerialName("next")
    data class Next<P : Comparable<P>>(
        /**
         * Return records with ordering field less or equal to this value
         */
        val maxOrdering: @Contextual P,
        /**
         * Return records with id less than provided id
         */
        val modelIdOffset: ModelOffset,

        override val size: Size
    ) : Page<P>() {

        override fun withMinSize(size: Size) = copy(size = this.size.minSize(size))
    }

    companion object Factory {
        fun <P : Comparable<P>> firstPage(size: Size): First<P> = First(size)
    }
}

fun <E, P : Comparable<P>> List<E>.nextPage(
    prevPage: Page<P>,
    maxOrdering: (E) -> P,
    offset: (E) -> ModelOffset
): Page.Next<P>? {
    return if (size < prevPage.sizeValue() || isEmpty()) {
        null
    } else {
        val lastElement = last()
        Page.Next(
            maxOrdering = maxOrdering(lastElement),
            modelIdOffset = offset(lastElement),
            size = prevPage.size
        )
    }
}
