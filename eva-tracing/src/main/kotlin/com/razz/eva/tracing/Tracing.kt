package com.razz.eva.tracing

import io.jaegertracing.internal.JaegerTracer
import io.jaegertracing.internal.metrics.Metrics
import io.jaegertracing.internal.metrics.NoopMetricsFactory
import io.jaegertracing.internal.propagation.B3TextMapCodec
import io.jaegertracing.internal.reporters.RemoteReporter
import io.jaegertracing.internal.samplers.ConstSampler
import io.jaegertracing.internal.senders.NoopSender
import io.jaegertracing.micrometer.MicrometerMetricsFactory
import io.jaegertracing.spi.Sender
import io.jaegertracing.thrift.internal.senders.UdpSender
import io.opentracing.Span
import io.opentracing.Tracer
import io.opentracing.propagation.Format
import io.opentracing.tag.StringTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

object Tracing {

    suspend inline fun <T> withNewSpan(
        tracer: Tracer,
        span: (Tracer) -> Span,
        crossinline block: suspend CoroutineScope.() -> T
    ): T {
        val builtSpan = span(tracer)
        return withContext(ActiveSpanElement(builtSpan)) {
            block()
        }
    }

    fun tracer(serviceName: String): Tracer = tracer(serviceName, Metrics(MicrometerMetricsFactory()), UdpSender())

    fun tracer(serviceName: String, metrics: Metrics, sender: Sender): Tracer {
        val b3Codec = B3TextMapCodec.Builder().build()
        return JaegerTracer.Builder(serviceName)
            .registerInjector(Format.Builtin.HTTP_HEADERS, b3Codec)
            .registerExtractor(Format.Builtin.HTTP_HEADERS, b3Codec)
            .registerInjector(Format.Builtin.TEXT_MAP, b3Codec)
            .registerExtractor(Format.Builtin.TEXT_MAP, b3Codec)
            .withReporter(
                RemoteReporter
                    .Builder()
                    .withMaxQueueSize(1000)
                    .withMetrics(metrics)
                    .withSender(sender)
                    .build()
            )
            .withSampler(ConstSampler(true))
            .build()
    }

    const val PERFORM = "perform"
    const val PERSIST = "persist"
    object Tags {
        val UOW_NAME = StringTag("uow.name")
        val UOW_OPERATION = StringTag("uow.operation")
        val EVENT_NAME = StringTag("event.name")
        val MODEL_NAME = StringTag("event.model.name")
        val TOPIC_NAME = StringTag("event.topic")
    }

    fun noopTracer(): Tracer = notReportingTracer()

    fun notReportingTracer(serviceName: String = "spec"): Tracer =
        tracer(serviceName, Metrics(NoopMetricsFactory()), NoopSender())
}
