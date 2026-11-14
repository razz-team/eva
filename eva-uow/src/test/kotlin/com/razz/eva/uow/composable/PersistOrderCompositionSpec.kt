@file:OptIn(TestDoubleApi::class)

package com.razz.eva.uow.composable

import com.razz.eva.domain.Employee
import com.razz.eva.domain.Model
import com.razz.eva.domain.TestModel
import com.razz.eva.domain.TestModel.CreatedTestModel
import com.razz.eva.domain.TestModel.Factory.createdTestModel
import com.razz.eva.events.UowEvent
import com.razz.eva.persistence.DummyConnection
import com.razz.eva.persistence.WithCtxConnectionTransactionManager
import com.razz.eva.repository.EntityRepos
import com.razz.eva.repository.EventRepository
import com.razz.eva.repository.ModelRepos
import com.razz.eva.repository.ModelRepository
import com.razz.eva.repository.hasRepo
import com.razz.eva.uow.BaseUnitOfWork
import com.razz.eva.uow.BaseUnitOfWork.Configuration
import com.razz.eva.uow.Changes
import com.razz.eva.uow.Clocks.fixedUTC
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.ExecutionStep
import com.razz.eva.uow.ExecutionStep.ModelAdded
import com.razz.eva.uow.ExecutionStep.ModelsAdded
import com.razz.eva.uow.PersistOrder
import com.razz.eva.uow.Persisting
import com.razz.eva.uow.SpyModelRepo
import com.razz.eva.uow.TestDoubleApi
import com.razz.eva.uow.TestPrincipal
import com.razz.eva.uow.UnitOfWorkExecutor
import com.razz.eva.uow.WrappingUnitOfWork
import com.razz.eva.uow.guarded.GuardedChangesDsl
import com.razz.eva.uow.guarded.GuardedUnitOfWork
import com.razz.eva.uow.guarded.Guarding
import com.razz.eva.uow.guarded.Proof
import com.razz.eva.uow.params.kotlinx.KotlinxParamsSerializer
import com.razz.eva.uow.stubChanges
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.opentelemetry.api.OpenTelemetry
import java.time.Instant.ofEpochMilli

// Test models by param2, ascending or descending; a data class, so two equal instances are one order.
private data class ByParam2(val descending: Boolean) : PersistOrder.Custom {
    override fun compare(a: Model<*, *>, b: Model<*, *>): Int {
        val ascending = param2(a).compareTo(param2(b))
        return if (descending) -ascending else ascending
    }

    private fun param2(model: Model<*, *>) = (model as CreatedTestModel).param2
}

private enum class Family { COMPOSABLE, WRAPPING }

// A composed child: its family, its configured order, the models it adds (none: it returns noChanges), the children
// it composes itself, and whether its parent catches what it throws.
private data class Child(
    val name: String,
    val family: Family,
    val order: PersistOrder,
    val models: List<CreatedTestModel>,
    val children: List<Child>,
    val caught: Boolean,
)

private class OrderedFamilyChild(
    executionContext: ExecutionContext,
    private val child: Child,
) : WrappingUnitOfWork<TestPrincipal, DummyUow.Params, String, FamilyDsl>(
    executionContext,
    Configuration(persistOrder = child.order),
),
    ComposableUow {
    override fun name() = child.name
    override fun wrap(dsl: ChangesDsl) = FamilyDsl(dsl)
    override suspend fun tryPerform(principal: TestPrincipal, params: DummyUow.Params): Changes<String> =
        if (child.models.isEmpty()) {
            noChanges(child.name)
        } else {
            changes {
                child.models.forEach { add(it) }
                child.name
            }
        }
}

private class OrderedChild(
    executionContext: ExecutionContext,
    private val child: Child,
) : UnitOfWork<TestPrincipal, DummyUow.Params, String>(executionContext, Configuration(persistOrder = child.order)) {
    override fun name() = child.name
    override suspend fun tryPerform(principal: TestPrincipal, params: DummyUow.Params): Changes<String> =
        if (child.models.isEmpty() && child.children.isEmpty()) {
            noChanges(child.name)
        } else {
            changes {
                child.models.forEach { add(it) }
                child.children.forEach { compose(it) }
                child.name
            }
        }
}

private suspend fun ChangesDsl.compose(child: Child) {
    try {
        when (child.family) {
            Family.COMPOSABLE -> execute({ ctx -> OrderedChild(ctx, child) }, TestPrincipal) { DummyUow.Params }
            Family.WRAPPING -> execute({ ctx -> OrderedFamilyChild(ctx, child) }, TestPrincipal) { DummyUow.Params }
        }
    } catch (ex: IllegalStateException) {
        if (!child.caught) throw ex
    }
}

private suspend fun GuardedChangesDsl<Proof<*>>.compose(child: Child) {
    try {
        when (child.family) {
            Family.COMPOSABLE -> execute({ ctx -> OrderedChild(ctx, child) }, TestPrincipal) { DummyUow.Params }
            Family.WRAPPING -> execute({ ctx -> OrderedFamilyChild(ctx, child) }, TestPrincipal) { DummyUow.Params }
        }
    } catch (ex: IllegalStateException) {
        if (!child.caught) throw ex
    }
}

// The two entry points of a composing parent: a composable unit of work and a wrapping family, the guarded one.
private enum class Parent { COMPOSABLE, GUARDED }

class PersistOrderCompositionSpec : FunSpec({

    val executionContext = ExecutionContext(fixedUTC(ofEpochMilli(0)), OpenTelemetry.noop())
    val ascending = ByParam2(descending = false)
    val descending = ByParam2(descending = true)
    fun model(param2: Long) = createdTestModel("model", param2)
    fun composable(name: String, order: PersistOrder, models: List<CreatedTestModel>, children: List<Child>) =
        Child(name, Family.COMPOSABLE, order, models, children, caught = false)
    fun wrapping(name: String, order: PersistOrder, models: List<CreatedTestModel>) =
        Child(name, Family.WRAPPING, order, models, listOf(), caught = false)
    fun parent(
        parent: Parent,
        order: PersistOrder,
        own: CreatedTestModel,
        children: List<Child>,
    ): (ExecutionContext) -> BaseUnitOfWork<TestPrincipal, DummyUow.Params, String, *> = when (parent) {
        Parent.COMPOSABLE -> { ctx ->
            object : UnitOfWork<TestPrincipal, DummyUow.Params, String>(ctx, Configuration(persistOrder = order)) {
                override fun name() = "Parent"
                override suspend fun tryPerform(principal: TestPrincipal, params: DummyUow.Params) = changes {
                    add(own)
                    children.forEach { compose(it) }
                    "parent"
                }
            }
        }
        Parent.GUARDED -> { ctx ->
            object : GuardedUnitOfWork<TestPrincipal, Proof<*>, DummyUow.Params, String>(
                ctx,
                Configuration(persistOrder = order),
                Guarding(Employee::class),
                setOf(),
            ) {
                override fun name() = "Parent"
                override suspend fun tryPerform(principal: TestPrincipal, params: DummyUow.Params) = changes {
                    add(own)
                    children.forEach { compose(it) }
                    "parent"
                }
            }
        }
    }
    suspend fun orderOf(uow: (ExecutionContext) -> BaseUnitOfWork<TestPrincipal, DummyUow.Params, String, *>) =
        uow(executionContext).tryPerform(TestPrincipal, DummyUow.Params).persistOrder
    // Runs [uow] through the executor over a spy repository that records each write in [history].
    suspend fun execute(
        uow: (ExecutionContext) -> BaseUnitOfWork<TestPrincipal, DummyUow.Params, String, *>,
        history: MutableList<ExecutionStep>,
    ) {
        @Suppress("UNCHECKED_CAST")
        val persisting = Persisting(
            transactionManager = WithCtxConnectionTransactionManager(
                connection = { DummyConnection },
                setPipelining = { true },
            ),
            modelRepos = ModelRepos(TestModel::class hasRepo SpyModelRepo(history) as ModelRepository<*, TestModel>),
            entityRepos = EntityRepos(),
            eventRepository = object : EventRepository {
                override suspend fun add(uowEvent: UowEvent) = Unit
            },
            paramsSerializer = KotlinxParamsSerializer(),
        )
        UnitOfWorkExecutor(listOf(), persisting, fixedUTC(ofEpochMilli(0)), OpenTelemetry.noop())
            .execute(TestPrincipal, uow) { DummyUow.Params }
    }
    suspend fun written(uow: (ExecutionContext) -> BaseUnitOfWork<TestPrincipal, DummyUow.Params, String, *>) =
        mutableListOf<ExecutionStep>().also { execute(uow, it) }
    fun refusal(unit: String, child: String, childOrder: PersistOrder, from: String, order: PersistOrder) =
        "$unit refuses composed unit of work [$child]: it writes in persist order [$childOrder], and [$from] " +
            "writes in [$order]. A composition writes in one order: give the units the same " +
            "PersistOrder.Custom, an object or a data class, as lambdas are never equal"

    Parent.entries.forEach { entry ->

        test("$entry parent: a child in Registration or Unspecified leaves the parent's order as it is") {
            listOf(PersistOrder.Registration, ascending, PersistOrder.Unspecified).forEach { parentOrder ->
                listOf(PersistOrder.Registration, PersistOrder.Unspecified).forEach { childOrder ->
                    val child = composable("Child", childOrder, listOf(model(1)), listOf())
                    orderOf(parent(entry, parentOrder, model(2), listOf(child))) shouldBe parentOrder
                }
            }
        }

        test("$entry parent: a custom child under a Registration parent carries its order up to the whole tree") {
            val own = model(3)
            val first = model(2)
            val second = model(1)
            val child = composable("Child", ascending, listOf(first, second), listOf())
            val uow = parent(entry, PersistOrder.Registration, own, listOf(child))
            orderOf(uow) shouldBe ascending
            written(uow).map { (it as ModelAdded<*, *>).model } shouldBe listOf(second, first, own)
        }

        test("$entry parent: a child with a custom order equal to the parent's own keeps it") {
            val child = composable("Child", ByParam2(descending = false), listOf(model(1)), listOf())
            orderOf(parent(entry, ascending, model(2), listOf(child))) shouldBe ascending
        }

        test("$entry parent: a child with another custom order than the parent's own is refused") {
            val child = composable("Child", descending, listOf(model(1)), listOf())
            shouldThrow<IllegalStateException> {
                orderOf(parent(entry, ascending, model(2), listOf(child)))
            }.message shouldBe refusal("Parent", "Child", descending, "Parent", ascending)
        }

        test("$entry parent: a custom child under an Unspecified parent is refused") {
            val child = composable("Child", ascending, listOf(model(1)), listOf())
            shouldThrow<IllegalStateException> {
                orderOf(parent(entry, PersistOrder.Unspecified, model(2), listOf(child)))
            }.message shouldBe refusal("Parent", "Child", ascending, "Parent", PersistOrder.Unspecified)
        }

        test("$entry parent: a child that registers nothing still carries its order, and is still refused") {
            val empty = composable("Empty", ascending, listOf(), listOf())
            orderOf(parent(entry, PersistOrder.Registration, model(2), listOf(empty))) shouldBe ascending
            shouldThrow<IllegalStateException> {
                orderOf(parent(entry, PersistOrder.Unspecified, model(2), listOf(empty)))
            }.message shouldBe refusal("Parent", "Empty", ascending, "Parent", PersistOrder.Unspecified)
        }

        test("$entry parent: a wrapping family's child carries its order up through its block") {
            listOf(listOf(model(1)), listOf()).forEach { models ->
                val child = wrapping("Family", ascending, models)
                orderOf(parent(entry, PersistOrder.Registration, model(2), listOf(child))) shouldBe ascending
            }
        }

        test("$entry parent: a grandchild's custom order carries up level by level") {
            val grandchild = composable("Grandchild", ascending, listOf(model(1)), listOf())
            val child = composable("Child", PersistOrder.Registration, listOf(model(5)), listOf(grandchild))
            orderOf(parent(entry, PersistOrder.Registration, model(3), listOf(child))) shouldBe ascending
            val conflicting = composable("Child", descending, listOf(model(5)), listOf(grandchild))
            shouldThrow<IllegalStateException> {
                orderOf(parent(entry, PersistOrder.Registration, model(3), listOf(conflicting)))
            }.message shouldBe refusal("Child", "Grandchild", ascending, "Child", descending)
        }

        test("$entry parent: a sibling with another custom order is refused before any write, naming the first") {
            val first = composable("First", ascending, listOf(model(1)), listOf())
            val second = composable("Second", descending, listOf(model(4)), listOf())
            val uow = parent(entry, PersistOrder.Registration, model(2), listOf(first, second))
            val history = mutableListOf<ExecutionStep>()
            shouldThrow<IllegalStateException> { execute(uow, history) }
                .message shouldBe refusal("Parent", "Second", descending, "First", ascending)
            history shouldBe listOf()
        }

        test("$entry parent: a parent that catches the refusal holds none of the child's rows and keeps its order") {
            val first = composable("First", ascending, listOf(model(1)), listOf())
            val refused = composable("Second", descending, listOf(model(4)), listOf()).copy(caught = true)
            val own = model(2)
            val uow = parent(entry, PersistOrder.Registration, own, listOf(first, refused))
            orderOf(uow) shouldBe ascending
            written(uow).map { (it as ModelAdded<*, *>).model.id() } shouldBe
                listOf(first.models.single().id(), own.id())
        }

        test("$entry parent: two lambda orders with the same body are refused") {
            val ownLambda = PersistOrder.Custom { a, b -> a.id().stringValue().compareTo(b.id().stringValue()) }
            val childLambda = PersistOrder.Custom { a, b -> a.id().stringValue().compareTo(b.id().stringValue()) }
            val child = composable("Child", childLambda, listOf(model(1)), listOf())
            shouldThrow<IllegalStateException> {
                orderOf(parent(entry, ownLambda, model(2), listOf(child)))
            }.message shouldBe refusal("Parent", "Child", childLambda, "Parent", ownLambda)
        }

        test("$entry parent: a parent with its own custom order writes in it at the top level") {
            val own = model(3)
            val childModel = model(1)
            val child = composable("Child", PersistOrder.Registration, listOf(childModel), listOf())
            written(parent(entry, ascending, own, listOf(child))).map { (it as ModelAdded<*, *>).model } shouldBe
                listOf(childModel, own)
        }
    }

    test("a plain unit of work in Unspecified batches its writes, and in a custom order sorts them") {
        val first = model(2)
        val second = model(1)
        fun plain(order: PersistOrder) = { ctx: ExecutionContext ->
            object : com.razz.eva.uow.UnitOfWork<TestPrincipal, DummyUow.Params, String>(
                ctx,
                Configuration(persistOrder = order),
            ) {
                override suspend fun tryPerform(principal: TestPrincipal, params: DummyUow.Params) = changes {
                    add(first)
                    add(second)
                    "plain"
                }
            }
        }
        written(plain(PersistOrder.Unspecified)).map { (it as ModelsAdded<*, *>).models } shouldBe
            listOf(listOf(first, second))
        written(plain(ascending)).map { (it as ModelAdded<*, *>).model } shouldBe listOf(second, first)
        written(plain(PersistOrder.Registration)).map { (it as ModelAdded<*, *>).model } shouldBe listOf(first, second)
    }

    test("a stubbed child carries no order up, whatever its configuration says") {
        val stubbed = { ctx: ExecutionContext ->
            object : UnitOfWork<TestPrincipal, DummyUow.Params, String>(ctx, Configuration(persistOrder = ascending)) {
                override suspend fun tryPerform(principal: TestPrincipal, params: DummyUow.Params) =
                    stubChanges("stubbed")
            }
        }
        val uow = { ctx: ExecutionContext ->
            object : UnitOfWork<TestPrincipal, DummyUow.Params, String>(ctx) {
                override suspend fun tryPerform(principal: TestPrincipal, params: DummyUow.Params) = changes {
                    add(model(1))
                    execute(stubbed, principal) { DummyUow.Params }
                }
            }
        }
        orderOf(uow) shouldBe PersistOrder.Registration
    }
})
