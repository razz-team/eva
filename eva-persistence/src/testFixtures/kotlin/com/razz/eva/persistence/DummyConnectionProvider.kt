package com.razz.eva.persistence

class DummyConnectionProvider : ConnectionProvider<DummyConnection> {

    override suspend fun acquire(): DummyConnection {
        return DummyConnection
    }

    override suspend fun release(connection: DummyConnection) = Unit
}
