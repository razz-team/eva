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

data class DbNodeAddress(private val hostPort: String) {
    constructor(host: String, port: Int) : this("$host:$port")
    override fun toString() = hostPort
    fun host() = hostPort.substringBefore(':')
    fun port() = hostPort.substringAfter(':').toInt()
}

data class DbName(private val name: String) {
    override fun toString() = name
}

data class DbUser(private val user: String) {
    override fun toString() = user
}

data class DbPassword(private val password: String) {

    fun showPassword() = password

    override fun toString() = "****"
}

data class JdbcURL(private val url: String) {
    override fun toString() = url
}

data class MaxPoolSize(private val maxPoolSize: Int) {
    override fun toString() = maxPoolSize.toString()
    fun value() = maxPoolSize
}

enum class ExecutorType {
    JDBC,
    VERTX
}
