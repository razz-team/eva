package com.razz.eva.uow

import com.razz.eva.domain.TestModel
import com.razz.eva.domain.TestModelId
import com.razz.eva.domain.TestModelId.Companion.randomTestModelId
import com.razz.eva.uow.ModelParam.Factory.idModelParam
import com.razz.eva.uow.ModelParam.Factory.modelParam
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.NothingSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.Duration
import java.util.UUID

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

    test("Model param re-queries a persisted model when constructed second time from model") {
        val oldModel = TestModel.existingCreatedTestModel(param1 = "lel", param2 = 1337)
        val newModel = TestModel.existingCreatedTestModel(id = oldModel.id(), param1 = "pek", param2 = 100500)
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

    test("Model param holds a New model on retry instead of re-querying a never-committed id") {
        val newModel = TestModel.createdTestModel("lel", 1337)
        val modelParam = InstantiationContext(1).modelParam(newModel) { error("must not re-query a New model") }
        modelParam.model().id() shouldBe newModel.id()
        modelParam.model().param1 shouldBe newModel.param1
    }

    test("Model param holds a Dirty model on retry instead of dropping its uncommitted events") {
        val dirtyModel = TestModel.existingCreatedTestModel(param1 = "lel", param2 = 1337).changeParam1("pek")
        val modelParam = InstantiationContext(1).modelParam(dirtyModel) { error("must not re-query a Dirty model") }
        modelParam.model().param1 shouldBe "pek"
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

    test("Id model param queries its model on first access and caches it") {
        val model = TestModel.existingCreatedTestModel(param1 = "lel", param2 = 1337)
        var queryCount = 0
        val modelParam = idModelParam(model.id()) { id ->
            queryCount++
            id shouldBe model.id()
            model
        }
        modelParam.id() shouldBe model.id()
        queryCount shouldBe 0
        modelParam.model().param1 shouldBe "lel"
        modelParam.model().param1 shouldBe "lel"
        queryCount shouldBe 1
    }

    test("Deserialized model param returns its id but refuses to load a model") {
        val idSerializer = object : KSerializer<TestModelId> {
            override val descriptor = String.serializer().descriptor
            override fun serialize(encoder: Encoder, value: TestModelId) = encoder.encodeString(value.stringValue())
            override fun deserialize(decoder: Decoder) = TestModelId(UUID.fromString(decoder.decodeString()))
        }
        val id = randomTestModelId()
        val serializer = ModelParam.Serializer(idSerializer, NothingSerializer())
        val modelParam = Json.decodeFromString(serializer, "\"${id.stringValue()}\"")
        modelParam.id() shouldBe id
        val ex = shouldThrow<IllegalStateException> { modelParam.model() }
        ex.message shouldContain "was deserialized and can not load its model"
    }
})

// Real time must elapse here, not coroutine time: the staleness check reads System.nanoTime(), which a
// virtual-time test dispatcher (delay) would not advance. Thread.sleep burns real wall-clock regardless of
// dispatcher. 20ms comfortably exceeds the 1ms staleAfter thresholds above, so the checks stay deterministic.
private const val STALENESS_SLEEP_MILLIS = 20L
