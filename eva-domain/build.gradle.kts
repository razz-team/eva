plugins {
    id("eva-kotlin")
    id("eva-publish")
}

dependencies {
    api(project(eva.eva_idempotency_key))
}
