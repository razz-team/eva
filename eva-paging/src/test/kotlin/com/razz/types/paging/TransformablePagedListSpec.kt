package com.razz.types.paging

import com.razz.eva.paging.Size
import com.razz.eva.paging.TransformablePagedList
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Instant.now

class TransformablePagedListSpec : BehaviorSpec({

    val now = now()

    Given("Paged list") {
        val pagedList = UserPagedList(
            listOf(User("Sergey", now), User("Ilya", now.minusSeconds(10))),
            Size(2),
        )

        And("Paged list with transformation") {
            val pagedListWithTransformation = TransformablePagedList(
                pagedList,
                object : Function1<User, String?> {
                    override fun invoke(p1: User) = p1.name
                },
            )

            When("Principal transforms paged list") {
                val transformedList = pagedListWithTransformation.transform()

                Then("Transformed list contains transformed items in the same order") {
                    transformedList shouldBe listOf("Sergey", "Ilya")
                }
            }

            When("Principal get the next page") {
                val nextPage = pagedListWithTransformation.nextPage()

                Then("Next page is the same as in the inner paged list") {
                    nextPage shouldBe pagedList.nextPage()
                }
            }
        }
    }
})
