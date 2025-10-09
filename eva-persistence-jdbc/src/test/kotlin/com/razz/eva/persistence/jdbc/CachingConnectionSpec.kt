package com.razz.eva.persistence.jdbc

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.sql.Connection
import java.sql.PreparedStatement

class CachingConnectionSpec : ShouldSpec({

    should("return cached prepared statement on repeated prepareStatement calls") {
        val mockPreparedStatement0 = mockk<PreparedStatement>()
        val mockConnection = mockk<Connection> {
            every { prepareStatement("query 0") } answers {
                mockPreparedStatement0
            }
        }
        val cachingConnection = CachingConnection(mockConnection)

        val sql = "query 0"
        val ps0 = cachingConnection.prepareStatement(sql)
        val ps1 = cachingConnection.prepareStatement(sql)

        ps0 shouldBe ps1
    }

    should("evict and recycle eldest prepared statement when capacity is exceeded") {
        val capacity = 3
        val mockPreparedStatements = List(capacity + 1) { index ->
            mockk<PreparedStatement> {
                every { close() } returns Unit
            }
        }
        val mockConnection = mockk<Connection> {
            mockPreparedStatements.forEachIndexed { index, ps ->
                every { prepareStatement("query $index") } answers { ps }
            }
        }
        val cachingConnection = CachingConnection(mockConnection, LruCache(capacity))

        val preparedStatements = mutableListOf<PreparedStatement>()
        repeat(capacity + 1) { index ->
            val ps = cachingConnection.prepareStatement("query $index")
            preparedStatements.add(ps)
        }

        // Verify that close was called on the evicted prepared statement
        verify { mockPreparedStatements[0].close() }
        // Verify that the other prepared statements are still in the cache
        mockPreparedStatements.drop(1).forEach {
            verify(exactly = 0) { it.close() }
        }
    }
})
