package com.razz.eva.uow.params.kotlinx

import com.razz.eva.IdempotencyKey
import com.razz.eva.domain.DepartmentId
import com.razz.eva.domain.DepartmentId.Companion.randomDepartmentId
import com.razz.eva.domain.EmployeeId
import com.razz.eva.domain.EmployeeId.Companion.randomEmployeeId
import com.razz.eva.domain.Name
import com.razz.eva.domain.Employee
import com.razz.eva.domain.Ration
import com.razz.eva.uow.ModelParam
import com.razz.eva.uow.ModelParam.Factory.idModelParam
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

class KotlinxParamsSerializerSpec : ShouldSpec({

    @Serializable
    data class SimpleParams(
        val id: Int,
        val name: String,
    ) : UowParams<SimpleParams> {
        override fun serialization() = serializer()
    }

    @Serializable
    data class ParamsWithIdempotencyKey(
        val amount: Long,
        override val idempotencyKey: IdempotencyKey,
    ) : UowParams<ParamsWithIdempotencyKey> {
        override fun serialization() = serializer()
    }

    @Serializable
    data class ComplexParams(
        val departmentId: DepartmentId,
        val employees: List<EmployeeId>,
        val names: List<Name>,
        val ration: Ration,
    ) : UowParams<ComplexParams> {
        override fun serialization() = serializer()
    }

    @Serializable
    data class ParamsWithModelParam(
        val employee: ModelParam<EmployeeId, @Contextual Employee>,
    ) : UowParams<ParamsWithModelParam> {
        override fun serialization() = serializer()
    }

    val serializer = KotlinxParamsSerializer()

    should("serialize simple params to valid JSON") {
        val params = SimpleParams(42, "test")
        val json = serializer.serialize(params)
        json shouldBe """{"id":42,"name":"test"}"""
    }

    should("serialize params with idempotency key") {
        val key = IdempotencyKey.random()
        val params = ParamsWithIdempotencyKey(100L, key)
        val json = serializer.serialize(params)
        json shouldBe """{"amount":100,"idempotencyKey":"${key.stringValue()}"}"""
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
        json shouldBe """{"departmentId":{"id":"${depId.id}"},"employees":[{"id":"${emp1.id}"},{"id":"${emp2.id}"}],"names":[{"first":"John","last":"Doe"},{"first":"Jane","last":"Smith"}],"ration":"BUBALEH"}"""
    }

    should("serialize params with model param") {
        val empId = randomEmployeeId()
        val params = ParamsWithModelParam(
            employee = idModelParam(empId) { TODO() },
        )
        val json = serializer.serialize(params)
        json shouldBe """{"employee":{"id":"${empId.id}"}}"""
    }

    should("throw when params do not implement kotlinx UowParams") {
        data class PlainParams(val x: Int) : com.razz.eva.uow.UowParams<PlainParams>

        val params = PlainParams(1)
        val error = shouldThrow<IllegalStateException> {
            serializer.serialize(params)
        }
        error.message shouldContain "PlainParams"
        error.message shouldContain "UowParams"
    }
})
