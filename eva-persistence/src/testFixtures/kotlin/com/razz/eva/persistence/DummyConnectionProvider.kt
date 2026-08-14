package com.razz.eva.persistence

class DummyConnectionProvider(
    override val endpoint: DbEndpoint = DbEndpoint("dummy", 5432, "dummy"),
) : ConnectionProvider<DummyConnection> {

    override suspend fun acquire(): DummyConnection {
        return DummyConnection
    }

    override suspend fun release(connection: DummyConnection) = Unit
}
