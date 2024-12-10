package com.razz.eva.persistence.config

data class DatabaseConfig(
    val nodes: List<DbNodeAddress>,
    val name: DbName,
    val user: DbUser,
    val password: DbPassword,
    val maxPoolSize: MaxPoolSize,
    val executorType: ExecutorType,
    val shouldInstrument: Boolean = true,
    val additionalProperties: Map<String, String> = emptyMap(),
) {
    val jdbcURL: JdbcURL = JdbcURL("jdbc:postgresql://${nodes.joinToString(",")}/$name")
}

data class DbNodeAddress(private val host: String, private val port: Int) {
    constructor(hostPort: String) : this(hostPort.substringBefore(':'), hostPort.substringAfter(':').toInt())
    override fun toString() = "$host:$port"
    fun host() = host
    fun port() = port
}

@JvmInline
value class DbName(private val name: String) {
    override fun toString() = name
}

@JvmInline
value class DbUser(private val user: String) {
    override fun toString() = user
}

@JvmInline
value class DbPassword(private val password: String) {

    fun showPassword() = password

    override fun toString() = "****"
}

@JvmInline
value class JdbcURL(private val url: String) {
    override fun toString() = url
}

@JvmInline
value class MaxPoolSize(private val maxPoolSize: Int) {
    override fun toString() = maxPoolSize.toString()
    fun value() = maxPoolSize
}

enum class ExecutorType {
    JDBC,
    VERTX
}
