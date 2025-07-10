package com.razz.eva.uow.func

import com.razz.eva.domain.Bubaleh
import com.razz.eva.domain.Department
import com.razz.eva.domain.DepartmentId
import com.razz.eva.domain.Employee
import com.razz.eva.persistence.config.DatabaseConfig
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.eva.repository.BubalehRepository
import com.razz.eva.repository.DepartmentRepository
import com.razz.eva.repository.EmployeeRepository
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
import com.razz.eva.uow.withFactory
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.util.*
import org.jooq.DMLQuery
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.InsertQuery
import org.jooq.Record
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

    val repos = ModelRepos(
        Department::class hasRepo departmentRepo,
        Employee::class hasRepo employeeRepo,
        Bubaleh::class hasRepo bubalehRepo
    )

    val writableRepository = WritableModelRepository(
        txnManager = transactionManager,
        clock = clock,
        modelRepos = repos
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
        dslContext = dslContext
    )

    val persisting = Persisting(
        transactionManager = transactionManager,
        modelRepos = repos,
        eventRepository = eventRepository,
    )

    val uowx = UnitOfWorkExecutor(
        factories = factories(clock),
        persisting = persisting,
        clock = clock,
        openTelemetry = openTelemetry,
    )

    private val patchingQueryExecutor = object : QueryExecutor by queryExecutor {
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

    val uowxInFuture = UnitOfWorkExecutor(
        factories = factories(fixedUTC(now + Duration.ofDays(6))),
        persisting = Persisting(
            transactionManager = transactionManager,
            modelRepos = repos,
            eventRepository = JooqEventRepository(
                queryExecutor = patchingQueryExecutor,
                dslContext = dslContext,
            )
        ),
        clock = fixedUTC(now + Duration.ofDays(6)),
        openTelemetry = openTelemetry,
    )

    val uowxRetries = UnitOfWorkExecutor(
        factories = listOf(
            HireEmployeesUow::class withFactory {
                HireEmployeesUow(executionContext, departmentRepo, employeeRepo, 1, false)
            }
        ),
        persisting = persisting,
        clock = clock,
        openTelemetry = openTelemetry,
    )

    fun factories(clock: Clock) = listOf(
        CreateEmployeeUow::class withFactory {
            CreateEmployeeUow(executionContext, departmentRepo)
        },
        CreateSoloDepartmentUow::class withFactory {
            CreateSoloDepartmentUow(executionContext, employeeRepo, departmentRepo)
        },
        HireEmployeesUow::class withFactory {
            HireEmployeesUow(executionContext, departmentRepo, employeeRepo, 0, true)
        }
    )
}
