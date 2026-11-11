package com.razz.eva.saga

import com.razz.eva.domain.Principal
import com.razz.eva.saga.TestSaga.Params
import com.razz.eva.saga.TestSaga.Terminal.Finish0
import com.razz.eva.saga.TestSaga.TestPrincipal
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import java.util.UUID.randomUUID

internal class SagaRunSpec : ShouldSpec({

    val principal = TestPrincipal(Principal.Id("cool-id"))
    val params = Params({ Finish0("it's time to stop") })

    should("start a run with no parent at attempt zero") {
        val run = SagaRun.first("TestSaga", principal, params)

        run.parentAttemptId shouldBe null
        run.attempt shouldBe 0
    }

    should("keep the run id and chain the attempt on restart") {
        val first = SagaRun.first("TestSaga", principal, params)

        val second = first.restarted()
        val third = second.restarted()

        listOf(second.runId, third.runId) shouldBe listOf(first.runId, first.runId)
        second.parentAttemptId shouldBe first.attemptId
        third.parentAttemptId shouldBe second.attemptId
        third.attempt shouldBe 2
        setOf(first.attemptId, second.attemptId, third.attemptId).size shouldBe 3
    }

    should("hand back the uuid a run id was built from") {
        val uuid = randomUUID()

        SagaRunId(uuid).uuidValue() shouldBe uuid
        SagaRunId(uuid).toString() shouldBe uuid.toString()
    }

    should("hand back the uuid an attempt id was built from") {
        val uuid = randomUUID()

        SagaAttemptId(uuid).uuidValue() shouldBe uuid
        SagaAttemptId(uuid).toString() shouldBe uuid.toString()
    }
})
