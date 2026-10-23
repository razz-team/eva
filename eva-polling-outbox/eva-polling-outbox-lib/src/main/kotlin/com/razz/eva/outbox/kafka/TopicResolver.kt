package com.razz.eva.outbox.kafka

import com.razz.eva.outbox.OutboxEvent

fun interface TopicResolver {

    fun topic(event: OutboxEvent): String

    companion object {
        fun single(topic: String): TopicResolver = TopicResolver { topic }

        /**
         * Default eva schema `<serviceName>-<modelName>`, e.g. `kebab-Babuleh`.
         */
        fun perServiceModel(serviceName: String): TopicResolver = TopicResolver { "$serviceName-${it.modelName}" }
    }
}
