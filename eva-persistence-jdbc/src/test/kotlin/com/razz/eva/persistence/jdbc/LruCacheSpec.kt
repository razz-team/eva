package com.razz.eva.persistence.jdbc

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class LruCacheSpec : ShouldSpec({

    should("evict eldest entry on insertion when capacity is exceeded") {
        val capacity = 1024
        val cache = LruCache<String, String>(capacity)
        repeat(1024) {
            val (added, evicted) = cache.cache("Key-$it") { _ -> "Value-$it" }
            added shouldBe "Value-$it"
            evicted.size shouldBe 0
        }
        cache.size shouldBe capacity
        val (added, evicted) = cache.cache("Key-Overflow") { _ -> "Value-Overflow" }
        added shouldBe "Value-Overflow"
        evicted.size shouldBe 1
        evicted[0] shouldBe "Value-0"
        cache.size shouldBe capacity
    }
})
