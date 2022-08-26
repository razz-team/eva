package com.razz.eva.examples.checkout

import com.razz.eva.domain.EntityState.PersistentState.Companion.persistentState
import com.razz.eva.domain.Principal
import com.razz.eva.domain.Version.Companion.V1
import com.razz.eva.examples.ServicePrincipal
import com.razz.eva.examples.composition.CheckoutUow
import com.razz.eva.examples.composition.account.Account
import com.razz.eva.examples.composition.account.Account.Factory.existingAccount
import com.razz.eva.examples.composition.account.AccountEvent.AccountDebited
import com.razz.eva.examples.composition.cart.Cart
import com.razz.eva.examples.composition.cart.Cart.CartItem
import com.razz.eva.examples.composition.cart.Cart.Factory.existingCart
import com.razz.eva.examples.composition.cart.Cart.State.CHECKED_OUT
import com.razz.eva.examples.composition.cart.Cart.State.SHOPPING
import com.razz.eva.examples.composition.cart.CartEvent.CartCheckedOut
import com.razz.eva.examples.composition.inventory.Inventory
import com.razz.eva.examples.composition.inventory.Inventory.Factory.existingInventory
import com.razz.eva.examples.composition.inventory.Inventory.InventoryItem
import com.razz.eva.examples.composition.inventory.InventoryEvent.StockReduced
import com.razz.eva.test.uow.UowBehaviorSpec
import com.razz.eva.uow.verify.verifyInOrder
import io.kotest.matchers.shouldBe

class CheckoutUowSpec : UowBehaviorSpec({

    Given("Existing cart, inventory and account") {
        val cart = existingCart(
            items = listOf(CartItem("iphone-silver-256", 999), CartItem("iphone-gold-256", 999)),
            state = SHOPPING,
            entityState = persistentState(V1),
        )
        val account = existingAccount(
            balance = 2000,
            entityState = persistentState(V1),
        )
        val inventory = existingInventory(
            stock = mapOf(InventoryItem("iphone-silver-256") to 10, InventoryItem("iphone-gold-256") to 20),
            entityState = persistentState(V1),
        )

        And("Params to checkout") {
            val params = CheckoutUow.Params(
                cartId = cart.id(),
                accountId = account.id(),
                inventoryId = inventory.id(),
            )

            And("Authorized principal") {
                val principal = ServicePrincipal(Principal.Id("1337"), Principal.Name("test"))

                And("CheckoutUow") {
                    val uow = CheckoutUow(
                        cartQueries = { id ->
                            require(id == cart.id())
                            cart
                        },
                        accountQueries = { id ->
                            require(id == account.id())
                            account
                        },
                        inventoryQueries = { id ->
                            require(id == inventory.id())
                            inventory
                        },
                        clock = clock,
                    )

                    When("Principal tries to perform UOW") {
                        val changes = uow.tryPerform(principal, params)

                        Then("Changes to credit contact, reduce inventory and checkout cart added") {
                            changes verifyInOrder {
                                updates<Account> {
                                    this.id() shouldBe account.id()
                                    this.balance shouldBe 2
                                }
                                emits<AccountDebited> {
                                    this.modelId shouldBe account.id()
                                    this.oldBalance shouldBe 2000
                                    this.newBalance shouldBe 2
                                }
                                updates<Inventory> {
                                    this.id() shouldBe inventory.id()
                                    this.stock shouldBe mapOf(
                                        InventoryItem("iphone-silver-256") to 9,
                                        InventoryItem("iphone-gold-256") to 19,
                                    )
                                }
                                emits<StockReduced> {
                                    this.modelId shouldBe inventory.id()
                                    this.items shouldBe mapOf(
                                        InventoryItem("iphone-silver-256") to 1,
                                        InventoryItem("iphone-gold-256") to 1,
                                    )
                                }
                                updates<Cart> {
                                    this.id() shouldBe cart.id()
                                    this.state shouldBe CHECKED_OUT
                                }
                                emits<CartCheckedOut> {
                                    this.modelId shouldBe cart.id()
                                }
                                returnsEq(cart.id())
                            }
                        }
                    }
                }
            }
        }
    }
})
