package com.razz.eva.outbox.kafka

import com.razz.eva.outbox.kafka.Sasl.ScramAlgorithm.SHA_512
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

class KafkaSecuritySpec : BehaviorSpec({

    Given("Plaintext security") {
        Then("Only the protocol is set") {
            KafkaSecurity.Plaintext.properties() shouldBe mapOf("security.protocol" to "PLAINTEXT")
        }
    }

    Given("Mutual TLS") {
        val security = KafkaSecurity.Ssl(
            trustStore = TrustStore("/certs/truststore.jks", "trust-s3cret"),
            keyStore = KeyStore("/certs/keystore.jks", "store-s3cret", keyPassword = "key-s3cret"),
        )

        Then("Trust store and key store are set") {
            security.properties() shouldBe mapOf(
                "security.protocol" to "SSL",
                "ssl.truststore.location" to "/certs/truststore.jks",
                "ssl.truststore.password" to "trust-s3cret",
                "ssl.keystore.location" to "/certs/keystore.jks",
                "ssl.keystore.password" to "store-s3cret",
                "ssl.key.password" to "key-s3cret",
            )
        }
        Then("Passwords are not printed") {
            security.toString() shouldNotContain "s3cret"
        }
    }

    Given("SCRAM over TLS") {
        val security = KafkaSecurity.SaslSsl(Sasl.Scram(SHA_512, "user", "secret"))

        Then("Mechanism and JAAS config are set") {
            security.properties() shouldBe mapOf(
                "security.protocol" to "SASL_SSL",
                "sasl.mechanism" to "SCRAM-SHA-512",
                "sasl.jaas.config" to "org.apache.kafka.common.security.scram.ScramLoginModule required " +
                    "username=\"user\" password=\"secret\";",
            )
        }
        Then("Password is not printed") {
            security.toString() shouldNotContain "secret"
        }
    }

    Given("A custom SASL mechanism") {
        val security = KafkaSecurity.SaslSsl(
            Sasl.Custom(
                mechanism = "AWS_MSK_IAM",
                jaasConfig = "software.amazon.msk.auth.iam.IAMLoginModule required;",
                properties = mapOf(
                    "sasl.client.callback.handler.class" to "software.amazon.msk.auth.iam.IAMClientCallbackHandler",
                ),
            ),
        )

        Then("Its extra properties are passed along") {
            security.properties() shouldBe mapOf(
                "security.protocol" to "SASL_SSL",
                "sasl.mechanism" to "AWS_MSK_IAM",
                "sasl.jaas.config" to "software.amazon.msk.auth.iam.IAMLoginModule required;",
                "sasl.client.callback.handler.class" to "software.amazon.msk.auth.iam.IAMClientCallbackHandler",
            )
        }
    }

    Given("Credentials with JAAS special characters") {
        val credentials = listOf(
            Sasl.Plain("us\"er", """pa\ss"word"""),
            Sasl.Scram(SHA_512, "us\"er", """pa\ss"word"""),
        )

        Then("Kafka accepts the generated JAAS config") {
            credentials.forEach { sasl ->
                OutboxProducer.create(
                    bootstrapServers = "localhost:9092",
                    security = KafkaSecurity.SaslPlaintext(sasl),
                ).close()
            }
        }
    }
})
