package com.razz.eva.test.uow

import com.razz.eva.domain.Model
import com.razz.eva.domain.ModelId
import com.razz.eva.uow.ModelParam
import io.opentelemetry.api.OpenTelemetry
import java.time.Clock

interface UowSpecBase {

    fun <MID : ModelId<out Comparable<*>>, M : Model<MID, *>> constantModelParam(
        model: M,
    ): ModelParam<MID, M> {
        return com.razz.eva.uow.TestConstantModelParam.constantModelParamForSpec(model)
    }

    fun executionContext(clock: Clock, otel: OpenTelemetry): com.razz.eva.uow.ExecutionContext {
        return com.razz.eva.uow.TestExecutionContext.executionContextForSpec(clock, otel)
    }
}
