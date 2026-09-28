plugins {
    id("eva-kotlin")
    id("eva-publish")
}

// the fixtures' witness mint needs the internal constructor
kotlin.target.compilations.getByName("testFixtures") {
    associateWith(kotlin.target.compilations.getByName("main"))
}

dependencies {
    api(project(eva.eva_idempotency_key))
}
