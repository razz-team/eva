package com.razz.eva.persistence

class DummyConnectionProvider(
    override val endpoint: DbEndpoint = DbEndpoint("dummy", 5432, "dummy", DbEndpoint.Role.PRIMARY),
) : ConnectionProvider<DummyConnection> {

    override suspend fun acquire(): DummyConnection {
        return DummyConnection
    }

    override suspend fun release(connection: DummyConnection) = Unit
}
