package com.razz.eva.uow.func

import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.UnregisteredUow
import com.razz.eva.uow.UowFactoryNotFoundException
import io.kotest.assertions.throwables.shouldThrow

class UnregisteredUowSpec : PersistenceBaseSpec({

    Given("Nothing really") {

        When("Principal tries to execute an unregistered UoW") {
            val attempt = suspend {
                module.uowx.execute(UnregisteredUow::class, TestPrincipal) {
                    UnregisteredUow.Params()
                }
            }

            Then("Uowx fails due to uow not being found") {
                shouldThrow<UowFactoryNotFoundException> { attempt() }
            }
        }
    }
})
