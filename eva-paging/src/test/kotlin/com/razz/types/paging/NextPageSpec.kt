package com.razz.types.paging

import com.razz.eva.paging.Page
import com.razz.eva.paging.Page.Factory.firstPage
import com.razz.eva.paging.Size
import com.razz.eva.paging.nextPage
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class NextPageSpec : BehaviorSpec({

    val now = Instant.now()
    data class User(
        val name: String,
        val timestamp: Instant,
    )

    Given("A previous page with some size") {
        val prevPage = firstPage<Instant>(Size(2))

        And("Result is empty") {
            val result = listOf<User>()

            When("Principal creates next page") {
                val nextPage = result.nextPage(prevPage, { it.timestamp }, { it.name })

                Then("Next page is null") {
                    nextPage shouldBe null
                }
            }
        }

        And("Result is same as page size") {
            val result = listOf(User("Sergey", now), User("Ilya", now.minusSeconds(10)))

            When("Principal creates next page") {
                val nextPage = result.nextPage(prevPage, { it.timestamp }, { it.name })

                Then("Next page is returned") {
                    nextPage shouldBe Page.Next(
                        maxOrdering = now.minusSeconds(10),
                        offset = "Ilya",
                        size = prevPage.size,
                    )
                }
            }
        }

        And("Result is less then page size") {
            val result = listOf(User("Sergey", now))

            When("Principal creates next page") {
                val nextPage = result.nextPage(prevPage, { it.timestamp }, { it.name })

                Then("Next page is null") {
                    nextPage shouldBe null
                }
            }
        }
    }
})
