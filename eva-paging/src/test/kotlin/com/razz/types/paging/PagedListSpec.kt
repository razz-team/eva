package com.razz.types.paging

import com.razz.eva.paging.AbstractPagedList
import com.razz.eva.paging.Size
import com.razz.eva.paging.TimestampPage
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.Instant.now

class PagedListSpec : BehaviorSpec({

    val now = now()

    data class User(
        val name: String,
        val timestamp: Instant
    )

    class UserPagedList(list: List<User>, pageSize: Size) : AbstractPagedList<User>(list, pageSize) {
        override fun maxTimestamp(item: User) = item.timestamp
        override fun offset(item: User) = item.name
    }

    Given("A current page's size") {
        val pageSize = Size(2)

        And("Result is empty") {
            val result = UserPagedList(listOf(), pageSize)

            When("Principal creates next page") {
                val nextPage = result.nextPage()

                Then("Next page is null") {
                    nextPage shouldBe null
                }
            }
        }

        And("List is of the page's size") {
            val users = listOf(User("Sergey", now), User("Ilya", now.minusSeconds(10)))
            val result = UserPagedList(users, pageSize)

            When("Principal creates next page") {
                val nextPage = result.nextPage()

                Then("Next page is returned") {
                    nextPage shouldBe TimestampPage.Next(
                        maxTimestamp = now.minusSeconds(10),
                        modelIdOffset = "Ilya",
                        size = pageSize
                    )
                }
            }
        }

        And("List's size is shorter than the page's size") {
            val users = listOf(User("Sergey", now))
            val result = UserPagedList(users, pageSize)

            When("Principal creates next page") {
                val nextPage = result.nextPage()

                Then("Next page is null") {
                    nextPage shouldBe null
                }
            }
        }
    }
})
