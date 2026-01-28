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
    implementation("team.razz.eva:eva-persistence-jdbc:$eva_version")
}
```

For snapshot version you also need to add the sonatype snapshots repository:
```kotlin
repositories {
    maven { url = URI.create("https://central.sonatype.com/repository/maven-snapshots/") }
}
```

### Model
Define events for your business domain.
You can read more about domain and integration events [here](https://docs.microsoft.com/en-us/dotnet/architecture/microservices/microservice-ddd-cqrs-patterns/domain-events-design-implementation). 
```kotlin
sealed class WalletEvent : ModelEvent<Wallet.Id> {

    override val modelName = "Wallet"

    data class Created(
        override val modelId: Wallet.Id,
        val currency: Currency,
        val amount: ULong,
        val expireAt: Instant
    ) : WalletEvent(), ModelCreatedEvent<Wallet.Id> {
        override fun integrationEvent() = buildJsonObject {
            put("currency", currency.currencyCode)
            put("amount", amount.toLong())
            put("expireAt", expireAt.epochSecond)
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

Define a model and methods that change model's state.
On any model's modification we should raise an event about it.
```kotlin
class Wallet(
    id: Id,
    val currency: Currency,
    val amount: ULong,
    val expireAt: Instant,
    modelState: ModelState<Id, WalletEvent>
) : Model<Wallet.Id, WalletEvent>(id, modelState) {

    data class Id(override val id: UUID) : ModelId<UUID>

    fun deposit(toDeposit: ULong) = Wallet(
        amount = amount - toDeposit,
        currency = currency,
        id = id(),
        expireAt = expireAt,
        modelState = modelState()
            .raiseEvent(WalletEvent.Deposit(id(), amount, toDeposit))
    )
}
```

### Entity
While Models are the primary building blocks for your domain, sometimes you need simpler data structures that don't require full lifecycle management. Entities serve this purpose.

```kotlin
data class Tag(
    val subjectId: UUID,
    val name: String,
    val value: String,
) : DeletableEntity()
```

Entities can be added and deleted within the same Unit of Work as Models:
```kotlin
override suspend fun tryPerform(principal: ServicePrincipal, params: Params): Changes<Unit> {
    return changes {
        update(department.addEmployee(employee))
        add(Tag(department.id().id, "employee-added", employee.id().toString()))
    }
}
```

Entity repositories extend `JooqBaseEntityRepository` or `JooqDeletableEntityRepository`:
```kotlin
class TagRepository(
    queryExecutor: QueryExecutor,
    dslContext: DSLContext,
) : JooqDeletableEntityRepository<Tag, TagRecord>(queryExecutor, dslContext, TAG) {
    
    override fun toRecord(entity: Tag): TagRecord = TagRecord().apply {
        subjectId = entity.subjectId
        name = entity.name
        value = entity.value
    }

    override fun fromRecord(record: TagRecord): Tag = Tag(
        subjectId = record.subjectId,
        name = record.name,
        value = record.value,
    )

    override fun entityCondition(entity: Tag): Condition =
        TAG.SUBJECT_ID.eq(entity.subjectId)
            .and(TAG.NAME.eq(entity.name))
}
```

Configure entity repositories alongside model repositories:
```kotlin
val persisting = Persisting(
    transactionManager = transactionManager,
    modelRepos = ModelRepos(Wallet::class hasRepo walletRepo),
    entityRepos = EntityRepos(Tag::class hasEntityRepo tagRepo),
    eventRepository = eventRepository,
)
```

### Unit of work
We need *queries* interface, so we can query our existing models
```kotlin
interface WalletQueries {
    suspend fun find(id: Wallet.Id): Wallet?
}
```
Now we can write our first unit of work.
In our framework unit of work stands for Command in CQRS pattern.
You can read more about CQRS [here](https://docs.microsoft.com/en-us/azure/architecture/patterns/cqrs) and [here](https://martinfowler.com/bliki/CQRS.html).
Unit of work is a transactional operation.
Here the unit of work either returns an existing wallet by ID or creates a new one and returns it.

```kotlin
class CreateWalletUow(
    private val queries: WalletQueries,
    clock: Clock
) : UnitOfWork<ServicePrincipal, Params, Wallet>(clock) {

    @Serializable
    data class Params(val id: String, val currency: String) : UowParams<Params> {
        override fun serialization() = serializer()
    }

    override suspend fun tryPerform(principal: ServicePrincipal, params: Params): Changes<Wallet> {
        val walletId = Wallet.Id(UUID.fromString(params.id))
        val wallet = queries.find(walletId)

        return if (wallet != null) {
            noChanges(wallet)
        } else {
            val amount = ULong.MIN_VALUE
            val currency = Currency.getInstance(params.currency)
            val expireAt = clock.instant().plus(timeToExpire)
            val newWallet = Wallet(
                id = walletId,
                currency = currency,
                amount = amount,
                expireAt = expireAt,
                modelState = newState(WalletEvent.Created(walletId, currency, amount, expireAt))
            )
            changes {
                add(newWallet)
            }
        }
    }
    companion object {
        private val timeToExpire = Duration.ofDays(600)
    }
}
```
In this example we return noChanges in case model with such id already exists, otherwise we create new model and using ChangesDsl we **add** new model.


You can also update your models in scope of unit of work - 
```kotlin
    changes {
        update(wallet.deposit(amount))    
    }
```
By default, **update** allows passing model with no changes (model was not updated after calling *deposit()* method).
Sometimes it can lead to some inconsistency in your domain logic - you expected model to be changed, but it wasn't.
You can force verification for your model, that it was changed in scope of your unit of work:
```kotlin
    changes {
        update(wallet.deposit(amount), required = true)    
    }
```

### Repository
To persist our model we need to add repository for it.
We use [jOOQ](https://www.jooq.org/) to have a type-safe DB querying.
You need to generate jOOQ tables/records based on your DB schema to have a type-safe mapping of your model to DB record.
You can use different Gradle plugins to generate jOOQ tables, f.e. check this [plugin](https://github.com/etiennestuder/gradle-jooq-plugin).

Your generated records should extend [BaseModelRecord](eva-jooq/src/main/kotlin/com/razz/jooq/record/BaseModelRecord.kt).
To achieve it use [jOOQ matcher strategies](https://www.jooq.org/doc/latest/manual/code-generation/codegen-matcherstrategy/).

When you create tables for your models you need to add next fields to your schema, so we can persist your model properly - 
```sql
  record_updated_at         TIMESTAMP      NOT NULL            ,
  record_created_at         TIMESTAMP      NOT NULL            ,
  version                   BIGINT         NOT NULL
```

After you created DB schema for you data, we can implement Repository for your model.
```kotlin
class WalletRepository(
    queryExecutor: QueryExecutor,
    dslContext: DSLContext
) : WalletQueries, JooqBaseModelRepository<UUID, Wallet.Id, Wallet, WalletEvent, WalletRecord>(
    queryExecutor = queryExecutor,
    dslContext = dslContext,
    table = WALLET
) {
    override fun toRecord(model: Wallet) = WalletRecord().apply {
        currency = model.currency.currencyCode
        amount = model.amount.toLong()
        expireAt = model.expireAt
    }

    override fun fromRecord(
        record: WalletRecord,
        modelState: PersistentState<Wallet.Id, WalletEvent>
    ) = Wallet(
        id = Wallet.Id(record.id),
        currency = Currency.getInstance(record.currency),
        amount = record.amount.toULong(),
        expireAt = record.expireAt,
        modelState = modelState
    )
}
```

### Configure it
We have unit of work and repository, so we can set up everything together.
In this example we are going to use JDBC implementation for our transactional manager.
```kotlin
class WalletModule(databaseConfig: DatabaseConfig) {

    /**
     * Query executor definition
     */
    val transactionManager = JdbcTransactionManager(
        primaryProvider = HikariPoolConnectionProvider(dataSource(databaseConfig, isPrimary = true)),
        replicaProvider = HikariPoolConnectionProvider(dataSource(databaseConfig, isPrimary = false)),
        blockingJdbcContext = newFixedThreadPool(databaseConfig.maxPoolSize.value()).asCoroutineDispatcher()
    )
    val queryExecutor = JdbcQueryExecutor(transactionManager)
    val dslContext: DSLContext = DSL.using(
        POSTGRES,
        Settings().withRenderNamedParamPrefix("$").withParamType(ParamType.NAMED)
    )

    /**
     * Persisting definition
     */
    val tracer = NoopTracerFactory.create()
    val walletRepo = WalletRepository(queryExecutor, dslContext)
    val persisting = Persisting(
        transactionManager = transactionManager,
        modelRepos = ModelRepos(Wallet::class hasRepo walletRepo),
        eventRepository = JooqEventRepository(queryExecutor, dslContext, tracer)
    )

    /**
     * Unit of work executor definition
     */
    val clock = Clock.tickMillis(UTC)
    val uowx: UnitOfWorkExecutor = UnitOfWorkExecutor(
        persisting = persisting,
        tracer = tracer,
        meterRegistry = SimpleMeterRegistry(),
        factories = listOf(
            CreateWalletUow::class withFactory { CreateWalletUow(walletRepo, clock) }
        )
    )
}
```

### Run it!
> Please don't forget to create tables for your models and table for storing events.
You can find script to create event's table [here](eva-events-db-schema/src/main/resources/com/razz/eva/events/db)

```kotlin
    val module = WalletModule(config)
    val principal = ServicePrincipal(Principal.Id("eva-id"), Principal.Name("eva"))
    
    val createdWallet = module.uowx.execute(CreateWalletUow::class, principal) {
        CreateWalletUow.Params(
            id = "45dfd599-4d62-47f1-8e47-a779df4f6bbc",
            currency = "USD"
        )
    }
```

## Design Philosophy

### Model vs Entity: When to Use Which

Eva provides two fundamental building blocks for your domain: **Models** and **Entities**. Understanding when to use each is crucial for clean domain design.

#### Use Model when:
- The object has a **distinct identity** that persists over time (e.g., User, Order, Wallet)
- You need to track **state changes** and emit **domain events**
- The object has a **lifecycle** with meaningful state transitions
- The object is an **aggregate root** in DDD terms

#### Use Entity when:
- The object's identity is defined by its **content/attributes** rather than a separate Id
- You need simple **add/delete** operations without lifecycle management
- The object represents **supplementary data** like tags, labels, allocations, or mappings
- No domain events need to be emitted for changes
- The object is essentially a **value object that needs persistence**

#### Key Differences

| Aspect | Model | Entity |
|--------|-------|--------|
| Identity | Explicit ID field (`ModelId`) | Implicit, defined by content |
| Versioning | Yes (`version` field for optimistic locking) | No |
| Events | Emits `ModelEvent` on state changes | No events |
| Lifecycle | Full lifecycle with state management | No lifecycle |
| Operations | `add`, `update`, `notChanged` | `add`, `delete` |
| Typical use | Aggregate roots, core domain objects | Tags, labels, mappings, allocations |

#### Example Decision

Consider a `Department` with employees and tags:

```kotlin
// Model: Has identity, lifecycle, emits events
class Department(
    id: Id,
    val name: String,
    val headcount: Int,
    modelState: ModelState<Id, DepartmentEvent>,
) : Model<Department.Id, DepartmentEvent>(id, modelState) {
    
    fun addEmployee(employee: Employee) = copy(
        headcount = headcount + 1,
        modelState = modelState().raiseEvent(EmployeeAdded(id(), employee.id(), headcount + 1)),
    )
}

// Entity: Identity is (subjectId + name), no lifecycle, no events
data class Tag(
    val subjectId: UUID,
    val name: String,
    val value: String,
) : DeletableEntity()
```

The `Department` is a Model because it's an aggregate root with identity, state transitions, and meaningful events. The `Tag` is an Entity because it's supplementary data whose identity is fully determined by its attributes.

### Implementation Details

#### Type Hierarchy
Models and Entities have **independent type hierarchies**. Both `Model` and `Entity` are abstract classes, which prevents a class from extending both:

```
Model<ID, E> (abstract class)
  └── Your domain models

Entity (abstract class)
  └── CreatableEntity (abstract class)
        └── DeletableEntity (abstract class)
              └── Your deletable entities
```

#### Entity Classes
- `CreatableEntity`: Can be added via `add()` in ChangesDsl
- `DeletableEntity`: Extends `CreatableEntity`, can also be deleted via `delete()` in ChangesDsl

Use `CreatableEntity` for append-only data (audit logs, historical records). Use `DeletableEntity` when entities can be removed.

#### Repository Pattern
Entity repositories follow the same pattern as Model repositories but without versioning:

```kotlin
// For entities that can only be added
interface EntityRepository<E : CreatableEntity> {
    suspend fun add(context: TransactionalContext, entity: E): E
    suspend fun add(context: TransactionalContext, entities: List<E>): List<E>
}

// For entities that can be added and deleted
interface DeletableEntityRepository<E : DeletableEntity> : EntityRepository<E> {
    suspend fun delete(context: TransactionalContext, entity: E): Boolean
    suspend fun delete(context: TransactionalContext, entities: List<E>): Int
}
```

#### Database Schema
Entity tables don't require `version` column but still need timestamp tracking:
```sql
CREATE TABLE tag (
    subject_id      UUID        NOT NULL,
    name            VARCHAR     NOT NULL,
    value           VARCHAR     NOT NULL,
    record_created_at TIMESTAMP NOT NULL,
    record_updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (subject_id, name)
);
```

#### Transactional Consistency
Models and Entities are persisted in the **same transaction**, ensuring consistency:

```kotlin
changes {
    update(department.addEmployee(employee))      // Model update
    add(Tag.tag(department.id().id, "new-hire", employee.name))  // Entity add
    delete(oldTag)                                 // Entity delete
}
// All changes committed atomically
```

## Features

### Event sourcing

#### Transactional outbox 
Eva employs [outbox pattern](https://microservices.io/patterns/data/transactional-outbox.html) for event distribution. In short: events are written to the same database and in the same transaction with models. Same transactional guarantees applied to both models and events. Events schema and migrations are provided by eva, you can find sql sources [here](/eva-events-db-schema/src/main/resources/com/razz/eva/events/db/V001__create_events.sql) and persistence logic [here](eva-repository/src/main/kotlin/com/razz/eva/repository/JooqEventRepository.kt). Eva is not in charge of further distribution of such events, however there are several opensource frameworks available, for instance [Kafka Connect](https://docs.confluent.io/platform/current/connect/index.html) and [Debezium](https://debezium.io/documentation/reference/2.0/tutorial.html).

#### Custom event publisher
When desired, events can be published through custom implementation of [EventPublisher](eva-events/src/main/kotlin/com/razz/eva/events/EventPublisher.kt). This publisher has to be passed to `Persisting` as optional parameter like demonstrated below:
```kotlin
val persisting: Persisting = Persisting(
    transactionManager = persistenceModule.transactionManager,
    modelRepos = repositoryModule.modelRepos,
    eventRepository = eventRepository,
    eventPublisher = eventPublisher
)
```
Events are passed to the publisher out of the scope of transaction once models are persisted. If persisting of models fails, no events are passed to the publisher. Publisher failure does not affect models persisting. Eva provides simple in-memory [eventbus](eva-eventbus/src/main/kotlin/com/razz/eva/eventbus/InMemoryEventBus.kt) implementation for your convenience. This eventbus implements `Publisher` interface and accepts multiple `EventConsumer`s to which it distributes published events. This implementation provides fifo guarantees for published events and does not provide any guarantees regarding distribution resilience. We strongly suggest to follow `transactional outbox` approach if at-least-once event delivery is a requirement. 

### Unit of work validation
After you wrote your first unit of work, you probably want to ask - how I can test it?

We provide verification DSL, so you can write unit tests and verify results of your unit of work.
Use `verifyInOrder` function to start verification process.
```kotlin
    CreateWalletUow(queries, clock).tryPerform(principal, params) verifyInOrder {
        // Model verification
        adds<Wallet> { model -> ... }
        addsEq(expectedModel)
        
        updates<Wallet> { model -> ... }
        updatesEq(expectedModel)

        // Entity verification (same methods, distinguished by type parameter)
        adds<Tag> { entity -> ... }
        addsEq(expectedTag)
        
        deletes<Tag> { entity -> ... }
        deletesEq(expectedTag)

        // Event verification
        emits<WalletEvent> { event -> ... }
        emitsEq(expectedEvent)
    
        returns { result -> ... }
    }
```
The `adds` and `addsEq` methods work for both Models and Entities - the correct verification is chosen based on the type parameter. Entity-specific methods `deletes` and `deletesEq` are available for `DeletableEntity` types.

You can check some examples [here](eva-uow/src/test/kotlin/com/razz/eva/uow/UnitOfWorkDemoSpec.kt)

### Idempotency
Sometimes something goes wrong, your service doesn't respond within deadline. You want to make a retry, but you are afraid of creating duplicates or new unnecessary models, so your DB becomes inconsistent.

To prevent it people use [idempotency key](https://stripe.com/docs/api/idempotent_requests) pattern.
Unit of work allows you to define idempotency key in params, so you can safely make retries.
```kotlin
    @Serializable
    data class Params(
        val id: String,
        val currency: String,
        override val idempotencyKey: IdempotencyKey
    ) : UowParams<Params> {
        override fun serialization() = serializer()
    }
```
Idempotency key can be shipped as a standalone artifact outside your service, if you f.e. want to pass it via http request.
```kotlin
    implementation("team.razz.eva:eva-idempotency-key:$eva_version")
```

### Paging
Out of the box Eva supports paging for your data, when it is not possible to return all results in one request.
First, you need to add paging module to your dependencies:
```kotlin
    implementation("team.razz.eva:eva-paging:$eva_version")
```
Second, you need to define your [PagingStrategy](eva-repository/src/main/kotlin/com/razz/eva/repository/PagingStrategy.kt). For now, we support paging by some timestamp only.
```kotlin
    object WalletPaging : PagingStrategy<UUID, Wallet.Id, Wallet, Wallet, WalletRecord>(Wallet::class) {

        override fun tableTimestamp() = WALLET.EXPIRE_AT
    
        override fun tableId() = WALLET.ID
    
        override fun tableOffset(modelOffset: ModelOffset) = UUID.fromString(modelOffset)
    
        override fun modelTimestamp(model: Wallet) = model.expireAt
    
        override fun modelOffset(model: Wallet) = model.id().stringValue()
    }
```
Now we can implement method in our repository to get pages.
```kotlin
    suspend fun wallets(currency: Currency, page: TimestampPage) = findPage(
        condition = WALLET.CURRENCY.eq(currency.currencyCode),
        page = page,
        pagingStrategy = WalletPaging
    )
```
That's all! This method returns object of [PagedList](eva-paging/src/main/kotlin/com/razz/eva/paging/PagedList.kt). It provides a part of your requested results and the next page to query the next part of results.

### Error handling
One day you are going face a lot of concurrent unit of works.
It leads to concurrent modification of same models. But our units of work are transactional, so we guarantee consistency of your models.
In case of concurrent modification unit of work throws [StaleRecordException](eva-persistence/src/main/kotlin/com/razz/eva/persistence/PersistenceException.kt).
By default, we will do a one retry for such kind of exception, but you can change this strategy
```kotlin
val configuration = UnitOfWork.Configuration(
    retry = StaleRecordFixedRetry(attempts = 3, staleRecordDelay = Duration.ofMillis(100))
)

class CreateWalletUow(
    private val queries: WalletQueries,
    clock: Clock
) : UnitOfWork<ServicePrincipal, Params, Wallet>(clock, configuration = configuration) {
```

Your database schema can also have some constraints, f.e. unique index. We don't want you to deal with such kind of exception out of your unit of work.
You can intercept these exceptions and throw your business exception or return some fallback result.

In our `CreateWalletUow` we check if wallet with same id already exists.
But we can face situation, when wallet with same id was created during unit of work execution.
Let's intercept this error and return already created wallet. We also can handle DB constraint and throw some more meaningful exception.

```kotlin
override suspend fun onFailure(params: Params, ex: PersistenceException): Wallet = when(ex) {
    is UniqueModelRecordViolationException -> checkNotNull(queries.find(Wallet.Id(UUID.fromString(params.id))))
    is ModelRecordConstraintViolationException -> throw IllegalArgumentException("${params.currency} is invalid")
    else -> throw ex
}
```

### Tracing and Monitoring
If you care about your system performance - you want to collect some metrics, so you can create alerts and investigate poor performance.
We allow you to collect some metrics via [Micrometer framework](https://micrometer.io/) and do instrumentation with [Opentracing](https://opentracing.io/).
Both frameworks provide you interfaces, and you can choose your own implementation, how you want to collect metrics.
In this example we use [Prometheus](https://prometheus.io/) and [Jaeger](https://www.jaegertracing.io/) implementations.
> Don't forget to add Jaeger and Prometheus dependencies to your project
```kotlin
    val meterRegistry = PrometheusMeterRegistry(DEFAULT)

    val uowx: UnitOfWorkExecutor = UnitOfWorkExecutor(
        persisting = persisting,
        tracer = tracer("wallet-service"),
        meterRegistry = meterRegistry,
        factories = listOf(
            CreateWalletUow::class withFactory { CreateWalletUow(walletRepo, clock) }
        )
    )
```

### Non-blocking persistence
In the begging we suggested you to add *eva-persistence-jdbc* to your dependencies and explained how to configure **JdbcTransactionManager**.
Under the hood it uses classic _blocking_ [Java JDBC driver](https://docs.oracle.com/javase/tutorial/jdbc/basics/processingsqlstatements.html).


But we also ship non-blocking version of *TransactionManager* - **VertxTransactionManager**, based on [Vert.x](https://vertx.io/docs/vertx-pg-client/java/).
Add this implementation to your dependencies
```kotlin
    implementation("team.razz.eva:eva-persistence-vertx:$eva_version")
```

Configuration:
```kotlin
val transactionManager = VertxTransactionManager(
    primaryProvider = PgPoolConnectionProvider(
        poolProvider(primaryConfig, true, meterRegistry)
    ),
    replicaProvider = PgPoolConnectionProvider(
        poolProvider(replicaConfig, false, meterRegistry)
    )
)
val queryExecutor = VertxQueryExecutor(transactionManager)

private fun poolProvider(config: DatabaseConfig, isPrimary: Boolean, meterRegistry: MeterRegistry): PgPool {
    val vertx = vertx(
        VertxOptions()
            .setMetricsOptions(
                MicrometerMetricsOptions()
                    .setMicrometerRegistry(meterRegistry)
                    .setLabels(setOf(POOL_NAME, POOL_TYPE, REMOTE, NAMESPACE))
                    .setEnabled(true)
            )
    )
    check(config.nodes.size == 1 || !isPrimary) {
        "Primary pool must be configured with single db node"
    }
    val options = config.nodes.map { node ->
        PgConnectOptions().apply {
            cachePreparedStatements = true
            preparedStatementCacheMaxSize = 2048
            preparedStatementCacheSqlFilter = Predicate { sql -> sql.length < 10_000 }
            pipeliningLimit = 256
            user = config.user.toString()
            password = config.password.showPassword()
            host = node.host()
            database = config.name.toString()
            port = node.port()
        }
    }
    return PgPool.pool(vertx, options, PoolOptions().apply { maxSize = config.maxPoolSize.value() })
}
```

# License
Eva is distributed under the terms of the Apache License (Version 2.0). See [license file](LICENSE) for details.
