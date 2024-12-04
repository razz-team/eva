package com.razz.eva.persistence.jdbc

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.sql.Connection
import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource

class DataSourceConnectionProviderSpec : BehaviorSpec({

    Given("DataSourceConnectionProvider with a data source that returns a connection after a timeout") {
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

        When("Acquire is called and cancelled after a short delay") {
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

            Then("The connection is acquired") {
                actual.get() shouldBeSameInstanceAs expected
            }
        }
    }
})
