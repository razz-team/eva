package com.razz.eva.test.repository

import com.razz.eva.migrations.DbSchema
import com.razz.eva.migrations.Migration
import com.razz.eva.migrations.Migrations
import com.razz.eva.persistence.TransactionManager
import com.razz.eva.persistence.config.ExecutorType
import com.razz.eva.persistence.executor.QueryExecutor
import com.razz.eva.persistence.jdbc.HikariPoolConnectionProvider
import com.razz.eva.persistence.jdbc.JdbcTransactionManager
import com.razz.eva.persistence.jdbc.executor.JdbcQueryExecutor
import com.razz.eva.persistence.vertx.PgPoolConnectionProvider
import com.razz.eva.persistence.vertx.VertxTransactionManager
import com.razz.eva.persistence.vertx.executor.VertxQueryExecutor
import com.razz.eva.test.db.DatabaseContainer
import com.razz.eva.test.db.DatabaseContainerHelper
import io.opentelemetry.api.OpenTelemetry.noop
import io.vertx.pgclient.PgConnectOptions
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.PoolOptions
import org.jooq.SQLDialect
import org.jooq.conf.ParamType
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import java.util.function.Predicate

open class RepositoryHelper(
    mainMigration: Migration,
    vararg otherMigrations: Migration = arrayOf(),
    createPartman: Boolean = false,
    trimmedPackagePrefix: String = "com/razz/",
    private val databaseContainer: DatabaseContainer = DatabaseContainer.BASIC,
) {

    constructor(
        migrationPath: String,
        additionalMigrationPaths: List<String> = listOf(),
        createPartman: Boolean = false,
        trimmedPackagePrefix: String = "com/razz/",
        schema: DbSchema = DbSchema.ModelsSchema,
        databaseContainer: DatabaseContainer = DatabaseContainer.BASIC,
    ) : this(
        mainMigration = Migration(migrationPath, schema, additionalMigrationPaths),
        otherMigrations = arrayOf<Migration>(),
        createPartman = createPartman,
        trimmedPackagePrefix = trimmedPackagePrefix,
        databaseContainer = databaseContainer,
    )

    companion object {
        val executorType: ExecutorType
            get() = System
                .getenv("PRIMARY_EXECUTOR_TYPE")
                ?.runCatching(ExecutorType::valueOf)
                ?.getOrNull()
                ?: ExecutorType.JDBC
    }

    open val hikariPoolSize = 4

    val dslContext = DSL.using(
        SQLDialect.POSTGRES,
        Settings().withRenderNamedParamPrefix("$").withParamType(ParamType.NAMED),
    )
    val txnManager: TransactionManager<*>
    val queryExecutor: QueryExecutor

    private val db = DatabaseContainerHelper.create(
        dbName = mainMigration.path
            .replace(trimmedPackagePrefix, "")
            .replace("/db", "")
            .replace("/", "") + "_repo_test",
        databaseContainer = databaseContainer,
    )

    init {
        db.createSchemas(createPartman)
        Migrations(databaseContainer.localPool(db.dbName(), 4), mainMigration, *otherMigrations).start()
        val (txnManager, queryExecutor) = when (executorType) {
            ExecutorType.JDBC -> jdbcEngine()
            ExecutorType.VERTX -> vertxEngine(db.dbName())
        }
        this.txnManager = txnManager
        this.queryExecutor = queryExecutor
    }

    private fun jdbcEngine(): Pair<TransactionManager<*>, QueryExecutor> {
        val pool = databaseContainer.localPool(db.dbName(), hikariPoolSize)
        val provider = HikariPoolConnectionProvider(pool)
        val jdbcManager = JdbcTransactionManager(provider, provider)
        return jdbcManager to JdbcQueryExecutor(jdbcManager, noop())
    }

    private fun vertxEngine(dbName: String): Pair<TransactionManager<*>, QueryExecutor> {
        val pool = vertxPool(db, dbName)
        val provider = PgPoolConnectionProvider(pool)
        val vertxManager = VertxTransactionManager(provider, provider)
        return vertxManager to VertxQueryExecutor(vertxManager)
    }

    private fun vertxPool(db: DatabaseContainerHelper, dbName: String): Pool {
        val options = PgConnectOptions().apply {
            cachePreparedStatements = true
            preparedStatementCacheMaxSize = 2048
            preparedStatementCacheSqlFilter = Predicate { sql -> sql.length < 10_000 }
            pipeliningLimit = 256
            user = db.username()
            password = db.password()
            host = db.dbHost()
            database = dbName
            port = db.dbPort()
        }
        return Pool.pool(options, PoolOptions())
    }
}
