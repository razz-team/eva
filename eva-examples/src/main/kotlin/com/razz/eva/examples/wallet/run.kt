package com.razz.eva.examples.wallet

import com.razz.eva.domain.Principal
import com.razz.eva.examples.ServicePrincipal
import com.razz.eva.persistence.config.DatabaseConfig
import com.razz.eva.persistence.config.DbName
import com.razz.eva.persistence.config.DbNodeAddress
import com.razz.eva.persistence.config.DbPassword
import com.razz.eva.persistence.config.DbUser
import com.razz.eva.persistence.config.ExecutorType
import com.razz.eva.persistence.config.MaxPoolSize
import com.razz.eva.uow.ModelParams
import kotlinx.coroutines.runBlocking

@Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")
fun main(vararg args: String) = runBlocking {
    val config = DatabaseConfig(
        nodes = listOf(DbNodeAddress("localhost", 5432)),
        name = DbName("eva-examples"),
        user = DbUser("test"),
        password = DbPassword("test"),
        maxPoolSize = MaxPoolSize(4),
        executorType = ExecutorType.JDBC
    )
    val module = WalletModule(config)
    val principal = ServicePrincipal(Principal.Id("eva-id"), Principal.Name("eva"))

    val createdWallet = module.uowx.execute(CreateWalletUow::class, principal) {
        CreateWalletUow.Params(
            id = "45dfd599-4d62-47f1-8e47-a779df4f6bbc",
            currency = "USD"
        )
    }

    val updatedWallet = module.uowx.execute(DepositWalletUow::class, principal, ModelParams {
        DepositWalletUow.Params(
            modelParam(createdWallet) { id -> requireNotNull(module.walletRepo.find(id)) },
            1337U,
        )
    })

    println(updatedWallet.amount)
}
