package com.razz.types.paging

import com.razz.eva.paging.BasicPagedList
import com.razz.eva.paging.Page
import com.razz.eva.paging.Page.First
import com.razz.eva.paging.Page.Next
import com.razz.eva.paging.PagedList
import com.razz.eva.paging.Pages
import com.razz.eva.paging.Size
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList

class PagesSpec : BehaviorSpec({

    Given("Pages returns two batches") {
        val pages = object : Pages<Int, Int>(2) {
            override suspend fun batch(page: Page<Int>): PagedList<Int, Int> = when (page) {
                is First -> BasicPagedList(listOf(1, 4), Next(4, "4", Size(2)))
                is Next -> BasicPagedList(listOf(5, 6), null)
            }
        }

        When("Principal requests flow by element") {
            val flow = pages.asFlow()

            Then("Flow has all elements") {
                flow.toList() shouldBe listOf(1, 4, 5, 6)
            }
        }

        When("Principal requests flow of batches") {
            val flow = pages.asBatchFlow()

            Then("Flow has all batches") {
                flow.toList() shouldBe listOf(listOf(1, 4), listOf(5, 6))
            }
        }
    }

    Given("Pages returns nothing") {
        val pages = object : Pages<Int, Int>(2) {
            override suspend fun batch(page: Page<Int>): PagedList<Int, Int> =
                BasicPagedList(emptyList(), null)
        }

        When("Principal requests flow by element") {
            val flow = pages.asFlow()

            Then("Flow has all elements") {
                flow.toList() shouldBe listOf()
            }
        }

        When("Principal requests flow of batches") {
            val flow = pages.asBatchFlow()

            Then("Flow has all batches") {
                flow.toList() shouldBe listOf(listOf())
            }
        }
    }
})
