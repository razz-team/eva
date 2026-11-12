package com.razz.eva.uow.composable

/**
 * Whether a unit of work can run as a composed child: [ComposableUow] says yes, [NotComposableUow] says no. The
 * type argument holds the answer, and a class can hold only one, so a unit cannot claim both.
 */
sealed interface Composition<A>
