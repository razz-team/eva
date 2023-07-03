package com.razz.eva.uow

import com.razz.eva.domain.TestModel
import com.razz.eva.uow.ModelParam.Factory.modelParam
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

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
})
