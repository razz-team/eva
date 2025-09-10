package com.razz.eva.examples.user

import com.razz.eva.domain.EntityState.PersistentState.Companion.persistentState
import com.razz.eva.domain.Principal
import com.razz.eva.domain.Version.Companion.V1
import com.razz.eva.examples.ServicePrincipal
import com.razz.eva.examples.changes.user.UpdateUserUow
import com.razz.eva.examples.changes.user.User
import com.razz.eva.examples.changes.user.User.Address
import com.razz.eva.examples.changes.user.User.Factory.existingUser
import com.razz.eva.examples.changes.user.User.FirstName
import com.razz.eva.examples.changes.user.User.LastName
import com.razz.eva.examples.changes.user.UserEvent.UserFirstNameChanged
import com.razz.eva.examples.changes.user.UserEvent.UserLastNameChanged
import com.razz.eva.examples.changes.user.UserQueries
import com.razz.eva.test.uow.UowBehaviorSpec
import com.razz.eva.uow.verify.verifyInOrder
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk

class UpdateUserUowSpec : UowBehaviorSpec({

    Given("Existing user") {
        val existingUser = existingUser(
            firstName = FirstName("Jane"),
            lastName = null,
            address = Address("123 Main St"),
            entityState = persistentState(V1, null),
        )
        val userQueries = mockk<UserQueries> {
            coEvery {
                get(existingUser.id())
            } returns existingUser
        }

        And("Params to update user") {
            val params = UpdateUserUow.Params(
                userId = existingUser.id(),
                firstName = FirstName("John"),
                lastName = LastName("Doe"),
                address = Address("123 Main St"),
            )

            And("Authorized principal") {
                val principal = ServicePrincipal(Principal.Id("1337"), Principal.Name("test"))

                And("UpdateUserUow") {
                    val uow = UpdateUserUow(userQueries, executionContext)

                    When("Principal tries to perform UOW") {
                        val changes = uow.tryPerform(principal, params)

                        Then("Changes to create and update contacts added") {
                            changes verifyInOrder {
                                updates<User> {
                                    id() shouldBe existingUser.id()
                                    this.firstName shouldBe FirstName("John")
                                    this.lastName shouldBe LastName("Doe")
                                    this.address shouldBe Address("123 Main St")
                                }
                                emits<UserFirstNameChanged> {
                                    modelId shouldBe existingUser.id()
                                    this.oldFirstName shouldBe FirstName("Jane")
                                    this.newFirstName shouldBe FirstName("John")
                                }
                                emits<UserLastNameChanged> {
                                    modelId shouldBe existingUser.id()
                                    this.oldLastName shouldBe null
                                    this.newLastName shouldBe LastName("Doe")
                                }
                                returns {
                                    id() shouldBe existingUser.id()
                                    this.firstName shouldBe FirstName("John")
                                    this.lastName shouldBe LastName("Doe")
                                    this.address shouldBe Address("123 Main St")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
})
