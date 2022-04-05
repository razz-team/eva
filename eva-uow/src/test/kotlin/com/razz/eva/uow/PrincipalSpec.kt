package com.razz.eva.uow

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class PrincipalSpec : BehaviorSpec({

    Given("Blank name") {
        val name = ""

        When("Create principal name") {
            val attempt = { Principal.Name(name) }

            Then("Fail to create principal name") {
                val exception = shouldThrow<IllegalArgumentException>(attempt)
                exception.message shouldBe "Name must be not blank"
            }
        }
    }

    Given("Long name") {
        val name = "a".repeat(101)

        When("Create principal name") {
            val attempt = { Principal.Name(name) }

            Then("Fail to create principal name") {
                val exception = shouldThrow<IllegalArgumentException>(attempt)
                exception.message shouldBe "Name is too long"
            }
        }
    }

    Given("Blank id") {
        val id = ""

        When("Create principal id") {
            val attempt = { Principal.Id(id) }

            Then("Fail to create principal name") {
                val exception = shouldThrow<IllegalArgumentException>(attempt)
                exception.message shouldBe "Id must be not blank"
            }
        }
    }

    Given("Long id") {
        val name = "a".repeat(101)

        When("Create principal name") {
            val attempt = { Principal.Id(name) }

            Then("Fail to create principal name") {
                val exception = shouldThrow<IllegalArgumentException>(attempt)
                exception.message shouldBe "Id is too long"
            }
        }
    }
})
