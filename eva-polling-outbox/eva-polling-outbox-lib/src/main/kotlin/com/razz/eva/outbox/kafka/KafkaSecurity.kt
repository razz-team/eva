package com.razz.eva.outbox.kafka

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol

/**
 * Connection security of the producer, one variant per `security.protocol`,
 * so that only settings relevant to the chosen protocol can be passed.
 * Serialized with a `type` discriminator: `plaintext`, `ssl`, `sasl_plaintext` or `sasl_ssl`.
 */
@Serializable
sealed interface KafkaSecurity {

    fun properties(): Map<String, Any>

    @Serializable
    @SerialName("plaintext")
    data object Plaintext : KafkaSecurity {
        override fun properties() = protocol(SecurityProtocol.PLAINTEXT)
    }

    /**
     * @property trustStore not needed when the broker certificate is signed by a CA the JVM trusts
     * @property keyStore client certificate for mutual TLS
     */
    @Serializable
    @SerialName("ssl")
    data class Ssl(
        val trustStore: TrustStore? = null,
        val keyStore: KeyStore? = null,
    ) : KafkaSecurity {
        override fun properties() = protocol(SecurityProtocol.SSL) +
            trustStore?.properties().orEmpty() +
            keyStore?.properties().orEmpty()
    }

    /**
     * Credentials of [Sasl.Plain] and [Sasl.Scram] are sent unencrypted, use [SaslSsl] outside of local setups.
     */
    @Serializable
    @SerialName("sasl_plaintext")
    data class SaslPlaintext(val sasl: Sasl) : KafkaSecurity {
        override fun properties() = protocol(SecurityProtocol.SASL_PLAINTEXT) + sasl.properties()
    }

    /**
     * @property trustStore not needed when the broker certificate is signed by a CA the JVM trusts
     */
    @Serializable
    @SerialName("sasl_ssl")
    data class SaslSsl(
        val sasl: Sasl,
        val trustStore: TrustStore? = null,
    ) : KafkaSecurity {
        override fun properties() = protocol(SecurityProtocol.SASL_SSL) +
            sasl.properties() +
            trustStore?.properties().orEmpty()
    }

    private companion object {
        fun protocol(protocol: SecurityProtocol): Map<String, Any> =
            mapOf(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to protocol.name)
    }
}

@Serializable
data class TrustStore(
    val location: String,
    val password: String,
) {
    fun properties(): Map<String, Any> = mapOf(
        SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to location,
        SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to password,
    )

    override fun toString() = "TrustStore(location=$location, password=$MASK)"
}

/**
 * @property keyPassword only needed when the key password differs from the keystore one
 */
@Serializable
data class KeyStore(
    val location: String,
    val password: String,
    val keyPassword: String? = null,
) {
    fun properties(): Map<String, Any> = buildMap {
        put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, location)
        put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, password)
        keyPassword?.let { put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, it) }
    }

    override fun toString() = "KeyStore(location=$location, password=$MASK, keyPassword=${keyPassword?.let { MASK }})"
}

/**
 * Serialized with a `type` discriminator: `plain`, `scram` or `custom`.
 */
@Serializable
sealed interface Sasl {

    fun properties(): Map<String, Any>

    @Serializable
    @SerialName("plain")
    data class Plain(val username: String, val password: String) : Sasl {
        override fun properties() = mapOf(
            SaslConfigs.SASL_MECHANISM to "PLAIN",
            SaslConfigs.SASL_JAAS_CONFIG to jaasConfig(
                "org.apache.kafka.common.security.plain.PlainLoginModule",
                username,
                password,
            ),
        )

        override fun toString() = "Plain(username=$username, password=$MASK)"
    }

    @Serializable
    @SerialName("scram")
    data class Scram(
        val algorithm: ScramAlgorithm,
        val username: String,
        val password: String,
    ) : Sasl {
        override fun properties() = mapOf(
            SaslConfigs.SASL_MECHANISM to algorithm.mechanism,
            SaslConfigs.SASL_JAAS_CONFIG to jaasConfig(
                "org.apache.kafka.common.security.scram.ScramLoginModule",
                username,
                password,
            ),
        )

        override fun toString() = "Scram(algorithm=$algorithm, username=$username, password=$MASK)"
    }

    /**
     * Any other mechanism, e.g. `GSSAPI`, `OAUTHBEARER` or `AWS_MSK_IAM`,
     * with the extra [properties] it needs, such as callback handler classes.
     */
    @Serializable
    @SerialName("custom")
    data class Custom(
        val mechanism: String,
        val jaasConfig: String,
        val properties: Map<String, String> = emptyMap(),
    ) : Sasl {
        override fun properties(): Map<String, Any> = properties + mapOf(
            SaslConfigs.SASL_MECHANISM to mechanism,
            SaslConfigs.SASL_JAAS_CONFIG to jaasConfig,
        )

        override fun toString() = "Custom(mechanism=$mechanism, jaasConfig=$MASK, properties=${properties.keys})"
    }

    enum class ScramAlgorithm(val mechanism: String) {
        SHA_256("SCRAM-SHA-256"),
        SHA_512("SCRAM-SHA-512"),
    }

    private companion object {
        fun jaasConfig(loginModule: String, username: String, password: String) =
            """$loginModule required username="${username.jaasEscaped()}" password="${password.jaasEscaped()}";"""

        fun String.jaasEscaped() = replace("\\", "\\\\").replace("\"", "\\\"")
    }
}

private const val MASK = "***"
