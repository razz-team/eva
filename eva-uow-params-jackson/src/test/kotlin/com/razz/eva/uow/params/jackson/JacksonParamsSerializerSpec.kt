package com.razz.eva.uow.params.jackson

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.razz.eva.domain.DepartmentId
import com.razz.eva.domain.DepartmentId.Companion.randomDepartmentId
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.EmployeeId.Companion.randomEmployeeId
import com.razz.eva.domain.Name
import com.razz.eva.domain.Ration
import com.razz.eva.uow.UowParams
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class JacksonParamsSerializerSpec : ShouldSpec({

    data class SimpleParams(
        val id: Int,
        val name: String,
    ) : UowParams<SimpleParams>

    data class ParamsWithNullable(
        val required: String,
        val optional: String? = null,
    ) : UowParams<ParamsWithNullable>

    data class ParamsWithDefaults(
        val value: Int = 42,
        val label: String = "default",
    ) : UowParams<ParamsWithDefaults>

    data class ComplexParams(
        val departmentId: DepartmentId,
        val employees: List<EmployeeId>,
        val names: List<Name>,
        val ration: Ration,
    ) : UowParams<ComplexParams>

    val serializer = JacksonParamsSerializer()
    val objectMapper = jacksonObjectMapper()

    should("serialize simple params to valid JSON") {
        val params = SimpleParams(42, "test")
        val json = serializer.serialize(params)

        val parsed = objectMapper.readValue<Map<String, Any>>(json)
        parsed["id"] shouldBe 42
        parsed["name"] shouldBe "test"
    }

    should("serialize params with nullable field set to null") {
        val params = ParamsWithNullable("hello")
        val json = serializer.serialize(params)

        json shouldContain "\"required\":\"hello\""
    }

    should("serialize params with nullable field set to value") {
        val params = ParamsWithNullable("hello", "world")
        val json = serializer.serialize(params)

        val parsed = objectMapper.readValue<Map<String, Any>>(json)
        parsed["required"] shouldBe "hello"
        parsed["optional"] shouldBe "world"
    }

    should("serialize params with default values") {
        val params = ParamsWithDefaults()
        val json = serializer.serialize(params)

        val parsed = objectMapper.readValue<Map<String, Any>>(json)
        parsed["value"] shouldBe 42
        parsed["label"] shouldBe "default"
    }

    should("serialize params with model IDs, lists, and enums") {
        val depId = randomDepartmentId()
        val emp1 = randomEmployeeId()
        val emp2 = randomEmployeeId()
        val params = ComplexParams(
            departmentId = depId,
            employees = listOf(emp1, emp2),
            names = listOf(Name("John", "Doe"), Name("Jane", "Smith")),
            ration = Ration.BUBALEH,
        )
        val json = serializer.serialize(params)

        val parsed = objectMapper.readValue<Map<String, Any>>(json)
        @Suppress("UNCHECKED_CAST")
        val departmentIdMap = parsed["departmentId"] as Map<String, Any>
        departmentIdMap["id"] shouldBe depId.id.toString()

        @Suppress("UNCHECKED_CAST")
        val employees = parsed["employees"] as List<Map<String, Any>>
        employees[0]["id"] shouldBe emp1.id.toString()
        employees[1]["id"] shouldBe emp2.id.toString()

        @Suppress("UNCHECKED_CAST")
        val names = parsed["names"] as List<Map<String, Any>>
        names[0]["first"] shouldBe "John"
        names[0]["last"] shouldBe "Doe"
        names[1]["first"] shouldBe "Jane"
        names[1]["last"] shouldBe "Smith"

        parsed["ration"] shouldBe "BUBALEH"
    }

    should("serialize using a custom ObjectMapper") {
        val customMapper = jacksonObjectMapper()
        val customSerializer = JacksonParamsSerializer(customMapper)
        val params = SimpleParams(1, "custom")
        val json = customSerializer.serialize(params)

        val parsed = customMapper.readValue<Map<String, Any>>(json)
        parsed["id"] shouldBe 1
        parsed["name"] shouldBe "custom"
    }
})
