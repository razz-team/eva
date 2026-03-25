package com.razz.eva.persistence.jdbc

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.runBlocking
import java.sql.Connection
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import javax.sql.DataSource
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

@OptIn(DelicateCoroutinesApi::class)
class DataSourceConnectionProviderSpec : ShouldSpec({

    should("acquire a connection even if the current coroutine is cancelled before acquiring it") {
        // data source set up with connection acquiring after a delay
        val dataSource = mockk<DataSource>()
        val expected = mockk<Connection>()
        every { dataSource.connection } answers {
            Thread.sleep(500)
            expected
        }

        // ensure there are 2 different dispatchers
        val blockingDispatcher = Dispatchers.IO
        val outerDispatcher = Dispatchers.Default

        val provider = DataSourceConnectionProvider(dataSource, blockingDispatcher)

        // try to acquire a connection in a separate coroutine and cancel it before the connection is actually acquired
        val actual = AtomicReference<Connection>()
        val job = launch(outerDispatcher) {
            var connection: Connection? = null
            try {
                connection = provider.acquire()
            } finally {
                actual.set(connection)
            }
        }

        delay(200)

        job.cancelAndJoin()

        // we should still get the connection
        actual.get() shouldBeSameInstanceAs expected
    }

    should("allow to release a connection even if all threads a busy") {
        // set up mocks
        val dataSource = mockk<DataSource>()

        val lock = ReentrantLock()
        val connectionCounter = AtomicInteger()
        val closed = CopyOnWriteArraySet<Int>()
        every { dataSource.connection } answers {
            lock.withLock {
                val number = connectionCounter.incrementAndGet()
                mockk {
                    every { close() } answers {
                        closed.add(number)
                    }
                }
            }
        }

        // dispatcher with 1 thread
        val dispatcher = newFixedThreadPoolContext(1, "jdbc-test")
        // ensure that poolMaxSize is configured to 1 as well,
        // i.e. even if there is a spare thread, we won't try to acquire it
        val provider = DataSourceConnectionProvider(
            pool = dataSource,
            blockingJdbcContext = dispatcher,
            poolMaxSize = 1,
        )

        // acquire a connection and lock, simulating that the connection pool is exhausted
        val acquiredConnection = provider.acquire()
        lock.lock()

        // try to acquire another connection, this should be pending until the first connection is released
        val anotherConnection = async { provider.acquire() }

        delay(200)

        // verify that nothing happened
        withClue("another connection is not yet acquired") {
            anotherConnection.isActive shouldBe true
        }
        connectionCounter.get() shouldBe 1
        closed shouldHaveSize 0

        // release the first connection, without the poolMaxSize specified,
        // the only thread will be busy with the second connection attempt
        println("releasing connection")
        provider.release(acquiredConnection)
        println("connection released")

        delay(200)
        // still no more new connection allocated, just one was closed
        connectionCounter.get() shouldBe 1
        closed shouldHaveSize 1

        // unlock the lock to simulate the connection being released back to the pool,
        // and the pending acquire can proceed
        lock.unlock()
        delay(200)

        withClue("another connection is acquired") {
            anotherConnection.isCompleted shouldBe true
        }
    }

    should("don't allow to release a connection even if all threads a busy") {
        // set up mocks
        val dataSource = mockk<DataSource>()

        val closed = AtomicInteger()
        val lock = ReentrantLock()
        every { dataSource.connection } answers {
            lock.withLock {
                mockk { every { close() } answers { closed.incrementAndGet() } }
            }
        }

        // executor with 1 thread
        val executor = Executors.newFixedThreadPool(1) {
            Thread(it, "jdbc-test").apply { isDaemon = true }
        }
        val dispatcher = executor.asCoroutineDispatcher()
        // we set the poolMaxSize to 2, which is more than the actual connection pool size
        val provider = DataSourceConnectionProvider(
            pool = dataSource,
            blockingJdbcContext = dispatcher,
            poolMaxSize = 2,
        )

        // acquire a connection and lock, simulating that the connection pool is exhausted
        val acquiredConnection = provider.acquire()
        lock.lock()

        // try to acquire another connection, this will also occupy the only thread
        val anotherConnection = async { provider.acquire() }

        delay(200)

        // verify that we are still trying to acquire the connection
        withClue("another connection is not yet acquired") {
            anotherConnection.isActive shouldBe true
        }

        // trying to release a connection will stuck and even can't be cancelled
        // because "scheduling" is not a suspension point, so we start it in a separate thread
        val thread = thread(isDaemon = true) { runBlocking { provider.release(acquiredConnection) } }

        delay(200)
        withClue("another connection is not yet acquired") {
            anotherConnection.isActive shouldBe true
        }
        withClue("release is still not completed") {
            thread.isAlive shouldBe true
        }
        closed.get() shouldBe 0

        // unlock the lock to simulate the connection being released back to the pool,
        // and the pending acquire can proceed
        lock.unlock()
        delay(200)

        withClue("another connection is acquired") {
            anotherConnection.isCompleted shouldBe true
        }
        withClue("release is completed") {
            thread.isAlive shouldBe false
        }
        closed.get() shouldBe 1
    }
})
