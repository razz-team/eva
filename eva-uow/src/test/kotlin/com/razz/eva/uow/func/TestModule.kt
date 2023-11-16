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
import com.razz.eva.tracing.Tracing.notReportingTracer
import com.razz.eva.uow.Clocks.fixedUTC
import com.razz.eva.uow.Clocks.millisUTC
import com.razz.eva.uow.CreateEmployeeUow
import com.razz.eva.uow.CreateSoloDepartmentUow
import com.razz.eva.uow.HireEmployeesUow
import com.razz.eva.uow.Persisting
import com.razz.eva.uow.UnitOfWorkExecutor
import com.razz.eva.uow.withFactory
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.StoreQuery
import org.jooq.Table
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant.now
import java.util.*

class TestModule(config: DatabaseConfig) : TransactionalModule(config) {

    val now = millisUTC().instant()
    val clock = fixedUTC(now)

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

    val tracer = TestTracer(notReportingTracer())

    val eventRepository = JooqEventRepository(
        queryExecutor = queryExecutor,
        dslContext = dslContext,
        tracer = tracer
    )

    val eventQueries = EventQueries(
        queryExecutor = queryExecutor,
        dslContext = dslContext
    )

    val persisting = Persisting(
        transactionManager = transactionManager,
        modelRepos = repos,
        eventRepository = eventRepository
    )

    val uowx = UnitOfWorkExecutor(
        factories = factories(clock),
        persisting = persisting,
        tracer = tracer,
        meterRegistry = SimpleMeterRegistry()
    )

    private val patchingQueryExecutor = object : QueryExecutor by queryExecutor {
        override suspend fun <RIN : Record, ROUT : Record> executeStore(
            dslContext: DSLContext,
            jooqQuery: StoreQuery<RIN>,
            table: Table<ROUT>,
        ): List<ROUT> {
            if (table.name == "uow_events") {
                val occurredAtParam = jooqQuery.getParam("6") as Field<Timestamp>
                jooqQuery.addValue(
                    DSL.field("inserted_at", SQLDataType.TIMESTAMP),
                    occurredAtParam,
                )
            }
            return queryExecutor.executeStore(dslContext, jooqQuery, table)
        }
    }

    val uowxInFuture = UnitOfWorkExecutor(
        factories = factories(fixedUTC(now() + Duration.ofDays(6))),
        persisting = Persisting(
            transactionManager = transactionManager,
            modelRepos = repos,
            eventRepository = JooqEventRepository(
                queryExecutor = patchingQueryExecutor,
                dslContext = dslContext,
                tracer = tracer
            )
        ),
        tracer = tracer,
        meterRegistry = SimpleMeterRegistry()
    )

    val uowxRetries = UnitOfWorkExecutor(
        factories = listOf(
            HireEmployeesUow::class withFactory {
                HireEmployeesUow(clock, departmentRepo, employeeRepo, 1, false)
            }
        ),
        persisting = persisting,
        tracer = tracer,
        meterRegistry = SimpleMeterRegistry()
    )

    fun factories(clock: Clock) = listOf(
        CreateEmployeeUow::class withFactory {
            CreateEmployeeUow(clock, departmentRepo)
        },
        CreateSoloDepartmentUow::class withFactory {
            CreateSoloDepartmentUow(clock, employeeRepo, departmentRepo)
        },
        HireEmployeesUow::class withFactory {
            HireEmployeesUow(clock, departmentRepo, employeeRepo, 0, true)
        }
    )
}
