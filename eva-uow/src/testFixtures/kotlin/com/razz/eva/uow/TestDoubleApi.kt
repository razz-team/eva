package com.razz.eva.uow

/**
 * [stubChanges] is for TEST DOUBLES only. It bypasses the changes DSL and every perform-time guard;
 * the executor refuses its output as a real UoW's outcome. Opt in from test sources only.
 */
@RequiresOptIn(
    message = "stubChanges builds Changes for test doubles; production code must go through changes { }",
    level = RequiresOptIn.Level.ERROR,
)
annotation class TestDoubleApi
