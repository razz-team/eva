package com.razz.eva.persistence.jdbc

import kotlin.collections.MutableMap.MutableEntry

/**
 * A LRU replacement strategy cache based on [LinkedHashMap] for prepared statements.
 */
internal class LruCache<K, V>(private val capacity: Int) : LinkedHashMap<K, V>(capacity, 0.75f, true) {
    private var removed: MutableList<V>? = null

    data class CachingResult<V>(val value: V, val evicted: List<V>)

    /**
     * Cache the value with the given key
     *
     * @return cached or retrieved value plus list of evicted values (can be empty)
     */
    fun cache(key: K, value: (K) -> V): CachingResult<V> {
        val cached = computeIfAbsent(key, value)
        if (removed != null) {
            val evicted = removed
            removed = null
            return CachingResult(cached, evicted!!)
        } else {
            return CachingResult(cached, listOf())
        }
    }

    override fun removeEldestEntry(eldest: MutableEntry<K, V>): Boolean {
        if (size > capacity) {
            if (removed == null) {
                removed = mutableListOf()
            }
            removed!!.add(eldest.value)
            return true
        } else {
            return false
        }
    }
}
