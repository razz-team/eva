package com.razz.eva.uow.func

import com.razz.eva.domain.Bubaleh
import com.razz.eva.domain.Department
import com.razz.eva.domain.DepartmentId
import com.razz.eva.domain.Employee
import com.razz.eva.events.EventPublisher
import com.razz.eva.events.UowEvent
import com.razz.eva.persistence.config.DatabaseConfig
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.eva.repository.BubalehRepository
import com.razz.eva.repository.DepartmentRepository
import com.razz.eva.repository.EmployeeRepository
import com.razz.eva.repository.EntityRepos
import com.razz.eva.repository.EventQueries
import com.razz.eva.repository.JooqEventRepository
import com.razz.eva.repository.ModelRepos
import com.razz.eva.repository.PreModifyCallback
import com.razz.eva.repository.hasRepo
import com.razz.eva.test.repository.WritableModelRepository
import com.razz.eva.test.tracing.OpenTelemetryTestConfiguration
import com.razz.eva.uow.Clocks.fixedUTC
import com.razz.eva.uow.Clocks.millisUTC
import com.razz.eva.uow.CreateEmployeeUow
import com.razz.eva.uow.CreateSoloDepartmentUow
import com.razz.eva.uow.ExecutionContext
import com.razz.eva.uow.HireEmployeesUow
import com.razz.eva.uow.Persisting
import com.razz.eva.uow.UnitOfWorkExecutor
import com.razz.eva.uow.WriteTxScope
import com.razz.eva.uow.params.kotlinx.KotlinxParamsSerializer
import com.razz.eva.uow.withFactory
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import java.sql.Timestamp
import java.time.Duration
import java.util.*
import org.jooq.DMLQuery
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.InsertQuery
import org.jooq.Record
import org.jooq.StoreQuery
import org.jooq.Table
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType

class TestModule(config: DatabaseConfig) : TransactionalModule(config) {

    val now = millisUTC().instant()
    val clock = fixedUTC(now)
    val executionContext = ExecutionContext(clock, OpenTelemetry.noop())

    val departmentPreUpdate = PreModifyCallback<UUID, DepartmentId, Department<*>>()

    val employeeRepo = EmployeeRepository(queryExecutor, dslContext)
    val departmentRepo = DepartmentRepository(queryExecutor, dslContext, departmentPreUpdate)
    val bubalehRepo = BubalehRepository(queryExecutor, dslContext)

    val modelRepos = ModelRepos(
        Department::class hasRepo departmentRepo,
        Employee::class hasRepo employeeRepo,
        Bubaleh::class hasRepo bubalehRepo,
    )
    val entityRepos = EntityRepos()

    val writableRepository = WritableModelRepository(
        txnManager = transactionManager,
        clock = clock,
        modelRepos = modelRepos,
    )

    val spanExporter = InMemorySpanExporter.create()
    val metricReader = InMemoryMetricReader.create()

    val openTelemetry = OpenTelemetryTestConfiguration.create(
        spanExporter = spanExporter,
        metricReader = metricReader,
    )

    val eventRepository = JooqEventRepository(
        queryExecutor = queryExecutor,
        dslContext = dslContext,
        openTelemetry = openTelemetry,
    )

    val eventQueries = EventQueries(
        queryExecutor = queryExecutor,
        dslContext = dslContext,
    )

    val persisting = Persisting(
        transactionManager = transactionManager,
        modelRepos = modelRepos,
        entityRepos = entityRepos,
        eventRepository = eventRepository,
        paramsSerializer = KotlinxParamsSerializer(),
    )

    val factories = listOf(
        CreateEmployeeUow::class withFactory {
            CreateEmployeeUow(executionContext, departmentRepo)
        },
        CreateSoloDepartmentUow::class withFactory {
            CreateSoloDepartmentUow(executionContext, employeeRepo, departmentRepo)
        },
        HireEmployeesUow::class withFactory {
            HireEmployeesUow(executionContext, departmentRepo, employeeRepo, 0, true, WriteTxScope.FLUSH)
        },
    )

    val uowx = UnitOfWorkExecutor(
        factories = factories,
        persisting = persisting,
        clock = clock,
        openTelemetry = openTelemetry,
    )

    private val patchingEventsQueryExecutor = object : QueryExecutor by queryExecutor {
        override suspend fun <R : Record> executeQuery(dslContext: DSLContext, jooqQuery: DMLQuery<R>): Int {
            if (jooqQuery is InsertQuery<*> && jooqQuery.sql.contains("uow_events")) {
                val occurredAtParam = jooqQuery.getParam("6") as Field<Timestamp>
                jooqQuery.addValue(
                    DSL.field("inserted_at", SQLDataType.TIMESTAMP),
                    occurredAtParam,
                )
            }
            return queryExecutor.executeQuery(dslContext, jooqQuery)
        }
    }

    private val patchingDepartmentsQueryExecutor = object : QueryExecutor by queryExecutor {
        override suspend fun <RIN : Record, ROUT : Record> executeStore(
            dslContext: DSLContext,
            jooqQuery: StoreQuery<RIN>,
            table: Table<ROUT>,
            returning: Collection<Field<*>>?,
        ): List<ROUT> {
            val rollingBack = dslContext.deleteQuery(DSL.table("departments")).apply {
                addConditions(DSL.condition("raise_serialization_failure()"))
            }
            queryExecutor.executeQuery(dslContext, rollingBack)
            return queryExecutor.executeStore(dslContext, jooqQuery, table, returning)
        }
    }

    val uowxInFuture = UnitOfWorkExecutor(
        factories = factories,
        persisting = Persisting(
            transactionManager = transactionManager,
            modelRepos = modelRepos,
            entityRepos = entityRepos,
            eventRepository = JooqEventRepository(
                queryExecutor = patchingEventsQueryExecutor,
                dslContext = dslContext,
            ),
            paramsSerializer = KotlinxParamsSerializer(),
        ),
        clock = fixedUTC(now + Duration.ofDays(6)),
        openTelemetry = openTelemetry,
    )

    val uowxRetries = UnitOfWorkExecutor(
        factories = listOf(
            HireEmployeesUow::class withFactory {
                HireEmployeesUow(executionContext, departmentRepo, employeeRepo, 1, false, WriteTxScope.FLUSH)
            },
        ),
        persisting = persisting,
        clock = clock,
        openTelemetry = openTelemetry,
    )

    val publishedUowEvents = Collections.synchronizedList(mutableListOf<UowEvent>())

    private val recordingEventPublisher = object : EventPublisher {
        override suspend fun publish(uowEvent: UowEvent) {
            publishedUowEvents += uowEvent
        }
    }

    private val publishingPersisting = Persisting(
        transactionManager = transactionManager,
        modelRepos = modelRepos,
        entityRepos = entityRepos,
        eventRepository = eventRepository,
        eventPublisher = recordingEventPublisher,
        paramsSerializer = KotlinxParamsSerializer(),
    )

    val uowxFullUowScope = UnitOfWorkExecutor(
        factories = listOf(
            HireEmployeesUow::class withFactory {
                HireEmployeesUow(executionContext, departmentRepo, employeeRepo, 1, true, WriteTxScope.FULL_UOW)
            },
        ),
        persisting = publishingPersisting,
        clock = clock,
        openTelemetry = openTelemetry,
    )

    val uowxFullUowScopeNoRetries = UnitOfWorkExecutor(
        factories = listOf(
            HireEmployeesUow::class withFactory {
                HireEmployeesUow(executionContext, departmentRepo, employeeRepo, 0, true, WriteTxScope.FULL_UOW)
            },
        ),
        persisting = publishingPersisting,
        clock = clock,
        openTelemetry = openTelemetry,
    )

    val uowxRollingBack = UnitOfWorkExecutor(
        factories = listOf(
            HireEmployeesUow::class withFactory {
                HireEmployeesUow(executionContext, departmentRepo, employeeRepo, 0, false, WriteTxScope.FLUSH)
            },
        ),
        persisting = Persisting(
            transactionManager = transactionManager,
            modelRepos = ModelRepos(
                Department::class hasRepo DepartmentRepository(
                    patchingDepartmentsQueryExecutor,
                    dslContext,
                    departmentPreUpdate,
                ),
                Employee::class hasRepo employeeRepo,
            ),
            entityRepos = entityRepos,
            eventRepository = eventRepository,
            paramsSerializer = KotlinxParamsSerializer(),
        ),
        clock = clock,
        openTelemetry = openTelemetry,
    )
}
