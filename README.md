![EVA](doc/wallpaper.jpg)
==========
[![Build Status](https://github.com/razz-team/eva/actions/workflows/main_build.yml/badge.svg)](https://github.com/razz-team/eva/actions)
[<img src="https://img.shields.io/maven-central/v/team.razz.eva/eva-domain.svg?label=latest%20release"/>](https://search.maven.org/search?q=g:team.razz.eva%20OR%20g:team.razz.eva)

Welcome to Eva! It is a Kotlin open-source framework, which helps you to write your code in DDD style and using CQRS approach.


## Getting started

Declare Eva dependencies in your project:

```kotlin
dependencies {
    implementation("team.razz.eva:eva-uow:$eva_version")
    implementation("team.razz.eva:eva-repository:$eva_version")
}
```

Define events for your business domain (full examples you can check here)
```kotlin
sealed class WalletEvent : ModelEvent<Wallet.Id> {

    override val modelName = "Wallet"

    data class Created(
        override val modelId: Wallet.Id,
        val currency: Currency,
        val amount: ULong
    ) : WalletEvent(), ModelCreatedEvent<Wallet.Id> {
        override fun integrationEvent() = buildJsonObject {
            put("currency", currency.currencyCode)
            put("amount", amount.toLong())
        }
    }

    data class Deposit(
        override val modelId: Wallet.Id,
        val walletAmount: ULong,
        val depositAmount: ULong
    ) : WalletEvent(), ModelCreatedEvent<Wallet.Id> {
        override fun integrationEvent() = buildJsonObject {
            put("walletAmount", walletAmount.toLong())
            put("depositAmount", depositAmount.toLong())
        }
    }
}
```

Define model and methods that changes model's state.
On any model's modification we should raise an event about this change.
```kotlin
class Wallet private constructor(
    id: Id,
    val currency: Currency,
    val amount: ULong,
    entityState: EntityState<Id, WalletEvent>
) : Model<Wallet.Id, WalletEvent>(id, entityState) {
    
    data class Id(override val id: UUID) : ModelId<UUID>
    
    fun deposit(toDeposit: ULong) = Wallet(
        amount = amount - toDeposit,
        currency = currency,
        id = id(),
        entityState = entityState()
            .raiseEvent(WalletEvent.Deposit(id(), amount, toDeposit))
    )
}
```

Now let's create queries interface, so we can query our existing models
```kotlin
interface WalletQueries {
    suspend fun find(id: Wallet.Id): Wallet?
}
```



```kotlin

```

## Features

### Idempotency

### Paging

### Error handling

### Tracing and Monitoring

### Event sourcing

# License
Eva is distributed under the terms of the Apache License (Version 2.0). See [license file](LICENSE) for details.
