package com.razz.eva.saga

import com.razz.eva.domain.Principal
import com.razz.eva.domain.Principal.Id
import com.razz.eva.domain.Principal.Name
import com.razz.eva.saga.TestSaga.Intermediary
import com.razz.eva.saga.TestSaga.Intermediary.Step0
import com.razz.eva.saga.TestSaga.Params
import com.razz.eva.saga.TestSaga.Terminal
import com.razz.eva.saga.TestSaga.TestPrincipal

internal object TestSaga : Saga<TestPrincipal, Params, Intermediary, Terminal, TestSaga>() {

    data class TestPrincipal(
        override val id: Id<String>,
        override val name: Name = Name("Test principal"),
    ) : Principal<String>

    sealed interface Intermediary : Saga.Intermediary<TestSaga> {
        data class Step0(val whatever: String) : Intermediary
        data class Step1(val whatever: String) : Intermediary
    }

    sealed interface Terminal : Saga.Terminal<TestSaga> {
        data class Finish0(val whatever: String) : Terminal
        data class Finish1(val whatever: String) : Terminal
    }

    data class Params(
        val succ: (Intermediary) -> Step<TestSaga>,
        val onException: (Exception, TestPrincipal, Params, Intermediary?) -> Terminal? = { e, _, _, _ -> throw e },
    )

    private lateinit var params: Params

    override suspend fun init(principal: TestPrincipal, params: Params): Step<TestSaga> {
        this.params = params
        return params.succ(Step0("started"))
    }

    override suspend fun next(
        principal: TestPrincipal,
        currentStep: Intermediary,
    ): Step<TestSaga> = params.succ(currentStep)

    override suspend fun onException(
        ex: Exception,
        principal: TestPrincipal,
        params: Params,
        currentStep: Intermediary?,
    ): Terminal? {
        return params.onException(ex, principal, params, currentStep)
    }
}
