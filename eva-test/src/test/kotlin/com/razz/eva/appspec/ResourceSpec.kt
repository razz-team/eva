package com.razz.eva.appspec

import com.razz.eva.test.WitnessedBody

// A base on top of an app base passes its body up; the receiver is a ResourceSpec at run time.
@Suppress("UNCHECKED_CAST")
abstract class ResourceSpec(
    body: WitnessedBody<ResourceSpec>,
) : AppSpec(body as WitnessedBody<AppSpec>) {

    val route = "/employees"
}
