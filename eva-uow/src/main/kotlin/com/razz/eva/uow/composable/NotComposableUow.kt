package com.razz.eva.uow.composable

/**
 * Marks a family whose units of work never run as children, for example one whose `finish` must judge every
 * change before it is written: a parent could extend what a child's block registered after that check. A class
 * cannot implement this and [ComposableUow], because the two give [Composition] different type arguments and the
 * compiler refuses that. A composable parent also refuses at run time a unit that carries both markers.
 */
interface NotComposableUow : Composition<NotComposableUow>
