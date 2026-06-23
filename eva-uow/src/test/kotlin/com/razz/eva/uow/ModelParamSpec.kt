package com.razz.eva.uow

import com.razz.eva.domain.TestModel
import com.razz.eva.uow.ModelParam.Factory.modelParam
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Duration

class ModelParamSpec : FunSpec({

    test("Model param returns id when constructed from model") {
        val model = TestModel.createdTestModel("lel", 1337)
        val modelParam = InstantiationContext(0).modelParam(model) { error("not used") }
        modelParam.id() shouldBe model.id()
    }

    test("Model param returns model when constructed from model") {
        val model = TestModel.createdTestModel("lel", 1337)
        val modelParam = InstantiationContext(0).modelParam(model) { error("not used") }
        modelParam.model().param1 shouldBe model.param1
        modelParam.model().param2 shouldBe model.param2
        modelParam.model().id() shouldBe model.id()
    }

    test("Model param returns model obtained from queries when constructed second time from model") {
        val oldModel = TestModel.createdTestModel("lel", 1337)
        val newModel = oldModel.changeParam1("pek").changeParam2(100500)
        var queryCount = 0
        val modelParam = InstantiationContext(1).modelParam(oldModel) { id ->
            queryCount++
            id shouldBe oldModel.id()
            newModel
        }
        modelParam.model().param1 shouldBe newModel.param1
        modelParam.model().param2 shouldBe newModel.param2
        modelParam.model().id() shouldBe oldModel.id()
        queryCount shouldBe 1
    }

    test("Model param re-queries when the held model is older than staleAfter") {
        val heldModel = TestModel.existingCreatedTestModel(param1 = "lel", param2 = 1337)
        var queryCount = 0
        val modelParam = InstantiationContext(0).modelParam(heldModel, Duration.ofMillis(1)) { id ->
            queryCount++
            id shouldBe heldModel.id()
            TestModel.existingCreatedTestModel(id = heldModel.id(), param1 = "pek", param2 = 100500)
        }
        Thread.sleep(STALENESS_SLEEP_MILLIS)
        val requeried = modelParam.model()
        requeried.param1 shouldBe "pek"
        requeried.param2 shouldBe 100500
        queryCount shouldBe 1
    }

    test("Model param keeps the held model when it is younger than staleAfter") {
        val heldModel = TestModel.existingCreatedTestModel(param1 = "lel", param2 = 1337)
        val modelParam = InstantiationContext(0).modelParam(heldModel, Duration.ofHours(1)) {
            error("not used")
        }
        modelParam.model().param1 shouldBe heldModel.param1
    }

    test("Model param never re-queries when staleAfter is null even if the held model is old") {
        val heldModel = TestModel.existingCreatedTestModel(param1 = "lel", param2 = 1337)
        val modelParam = InstantiationContext(0).modelParam(heldModel) { error("not used") }
        Thread.sleep(STALENESS_SLEEP_MILLIS)
        modelParam.model().param1 shouldBe heldModel.param1
    }
})

// Real time must elapse here, not coroutine time: the staleness check reads System.nanoTime(), which a
// virtual-time test dispatcher (delay) would not advance. Thread.sleep burns real wall-clock regardless of
// dispatcher. 20ms comfortably exceeds the 1ms staleAfter thresholds above, so the checks stay deterministic.
private const val STALENESS_SLEEP_MILLIS = 20L
