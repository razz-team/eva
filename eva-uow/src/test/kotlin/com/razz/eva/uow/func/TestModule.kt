package com.razz.eva.uow.func

import com.razz.eva.domain.Bubaleh
import com.razz.eva.domain.Department
import com.razz.eva.domain.DepartmentId
import com.razz.eva.domain.Employee
import com.razz.eva.persistence.config.DatabaseConfig
import com.razz.eva.repository.BubalehRepository
import com.razz.eva.repository.DepartmentRepository
import com.razz.eva.repository.EmployeeRepository
import com.razz.eva.repository.EventQueries
import com.razz.eva.repository.JooqEventRepository
import com.razz.eva.repository.ModelRepos
import com.razz.eva.repository.PreModifyCallback
import com.razz.eva.repository.ShakshoukaRepository
import com.razz.eva.repository.hasRepo
import com.razz.eva.test.repository.WritableModelRepository
import com.razz.eva.tracing.Tracing.notReportingTracer
import com.razz.eva.uow.Clocks.fixedUTC
import com.razz.eva.uow.Clocks.millisUTC
import com.razz.eva.uow.CookUow
import com.razz.eva.uow.CreateDepartmentUow
import com.razz.eva.uow.CreateEmployeeUow
import com.razz.eva.uow.CreateSoloDepartmentUow
import com.razz.eva.uow.HireEmployeesUow
import com.razz.eva.uow.InternalMobilityUow
import com.razz.eva.uow.PartyHardUow
import com.razz.eva.uow.Persisting
import com.razz.eva.uow.UnitOfWorkExecutor
import com.razz.eva.uow.withFactory
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.mockk
import java.time.Clock
import java.time.Duration
import java.time.Instant.now
import java.util.*

class TestModule(config: DatabaseConfig) : TransactionalModule(config) {

    val now = millisUTC().instant()
    val clock = fixedUTC(now)

    val departmentPreUpdate = mockk<PreModifyCallback<UUID, DepartmentId, Department<*>>>(relaxed = true)

    val employeeRepo = EmployeeRepository(queryExecutor, dslContext)
    val departmentRepo = DepartmentRepository(queryExecutor, dslContext, departmentPreUpdate)
    val bubalehRepo = BubalehRepository(queryExecutor, dslContext)
    val shakshoukaRepo = ShakshoukaRepository(queryExecutor, dslContext)

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

    val eventRepository = JooqEventRepository(
        queryExecutor = queryExecutor,
        dslContext = dslContext,
        tracer = notReportingTracer()
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

    val tracer = TestTracer(notReportingTracer())

    val uowx = UnitOfWorkExecutor(
        factories = factories(clock),
        persisting = persisting,
        tracer = tracer,
        meterRegistry = SimpleMeterRegistry()
    )

    val uowxInFuture = UnitOfWorkExecutor(
        factories = factories(fixedUTC(now() + Duration.ofDays(6))),
        persisting = persisting,
        tracer = tracer,
        meterRegistry = SimpleMeterRegistry()
    )

    fun factories(clock: Clock) = listOf(
        CreateDepartmentUow::class withFactory {
            CreateDepartmentUow(clock, departmentRepo)
        },
        CreateEmployeeUow::class withFactory {
            CreateEmployeeUow(clock, departmentRepo)
        },
        CreateSoloDepartmentUow::class withFactory {
            CreateSoloDepartmentUow(clock, employeeRepo, departmentRepo)
        },
        HireEmployeesUow::class withFactory {
            HireEmployeesUow(clock, departmentRepo)
        },
        CookUow::class withFactory {
            CookUow(clock, employeeRepo)
        },
        PartyHardUow::class withFactory {
            PartyHardUow(clock, departmentRepo, shakshoukaRepo, bubalehRepo)
        },
        InternalMobilityUow::class withFactory {
            InternalMobilityUow(clock, employeeRepo, departmentRepo)
        }
    )
}
