package com.razz.eva.persistence

class DummyConnectionProvider : ConnectionProvider<DummyConnection> {

    override val endpoint = DbEndpoint("dummy", 5432, "dummy")

    override suspend fun acquire(): DummyConnection {
        return DummyConnection
    }

    override suspend fun release(connection: DummyConnection) = Unit
}
