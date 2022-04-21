package com.razz.eva.uow

import com.razz.eva.domain.Bubaleh
import com.razz.eva.domain.Department
import com.razz.eva.domain.Employee
import com.razz.eva.persistence.config.DatabaseConfig
import com.razz.eva.repository.BubalehRepository
import com.razz.eva.repository.DepartmentRepository
import com.razz.eva.repository.EmployeeRepository
import com.razz.eva.repository.JooqEventRepository
import com.razz.eva.repository.ModelRepos
import com.razz.eva.repository.ShakshoukaRepository
import com.razz.eva.repository.hasRepo
import com.razz.eva.tracing.Tracing.notReportingTracer
import com.razz.eva.uow.Clocks.fixedUTC
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.Clock
import java.time.Duration
import java.time.Instant.now

class TestModule(config: DatabaseConfig) : TransactionalModule(config) {

    val employeeRepo = EmployeeRepository(queryExecutor, dslContext)
    val departmentRepo = DepartmentRepository(queryExecutor, dslContext)
    val bubalehRepo = BubalehRepository(queryExecutor, dslContext)
    val shakshoukaRepo = ShakshoukaRepository(queryExecutor, dslContext)

    val repos = ModelRepos(
        Department::class hasRepo departmentRepo,
        Employee::class hasRepo employeeRepo,
        Bubaleh::class hasRepo bubalehRepo
    )

    val eventRepository = JooqEventRepository(
        queryExecutor = queryExecutor,
        dslContext = dslContext,
        tracer = notReportingTracer()
    )

    val persisting = Persisting(
        transactionManager = transactionManager,
        modelRepos = repos,
        eventRepository = eventRepository
    )

    val uowx = UnitOfWorkExecutor(
        factories = factories(fixedUTC(now())),
        persisting = persisting,
        tracer = notReportingTracer(),
        meterRegistry = SimpleMeterRegistry()
    )

    val uowxInFuture = UnitOfWorkExecutor(
        factories = factories(fixedUTC(now() + Duration.ofDays(6))),
        persisting = persisting,
        tracer = notReportingTracer(),
        meterRegistry = SimpleMeterRegistry()
    )

    private fun factories(clock: Clock) = listOf(
        CreateEmployeeUow::class withFactory { CreateEmployeeUow(clock, departmentRepo) },
        CreateSoloDepartmentUow::class withFactory { CreateSoloDepartmentUow(clock, employeeRepo, departmentRepo) },
        HireEmployeesUow::class withFactory { HireEmployeesUow(clock, departmentRepo) },
        TapUow::class withFactory { TapUow(clock, employeeRepo) },
        PartyHardUow::class withFactory { PartyHardUow(clock, departmentRepo, shakshoukaRepo, bubalehRepo) },
    )
}
