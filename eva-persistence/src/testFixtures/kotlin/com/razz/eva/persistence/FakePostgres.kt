package com.razz.eva.persistence

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * A fake postgres speaking just enough of the v3 wire protocol to fail on purpose. It either refuses
 * at startup or completes the handshake and answers the first statement with a byte-correct FATAL
 * ErrorResponse carrying the given sql state. Tests drive the real driver decode path for states which
 * are operationally awkward to produce, for example `53300` without exhausting a slot table or `57P02`
 * without crashing anything.
 */
class FakePostgres private constructor(
    private val mode: Mode,
    private val sqlState: String,
) : AutoCloseable {

    private enum class Mode { STARTUP_ERROR, STATEMENT_ERROR, STATEMENT_CLOSE }

    private val server = ServerSocket(0)

    init {
        thread(isDaemon = true, name = "fake-postgres-${server.localPort}") { acceptLoop() }
    }

    val port: Int get() = server.localPort

    fun jdbcUrl(): String =
        "jdbc:postgresql://localhost:$port/fake?sslmode=disable&assumeMinServerVersion=9.4&user=fake"

    private fun acceptLoop() {
        while (!server.isClosed) {
            val socket = try {
                server.accept()
            } catch (ex: Exception) {
                return
            }
            try {
                socket.use { serve(it) }
            } catch (ex: Exception) {
                // the driver under test closed mid-conversation, nothing to serve
            }
        }
    }

    private fun serve(socket: Socket) {
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())
        readStartup(input, output)
        when (mode) {
            Mode.STARTUP_ERROR -> writeErrorResponse(output)
            Mode.STATEMENT_ERROR -> {
                writeHandshake(output)
                input.readByte()
                // close right after the error: pgjdbc surfaces the recorded FATAL on EOF, and it
                // deadlocks on a still-open socket waiting for a ReadyForQuery which never comes
                writeErrorResponse(output)
            }
            Mode.STATEMENT_CLOSE -> {
                writeHandshake(output)
                input.readByte()
            }
        }
    }

    private fun readStartup(input: DataInputStream, output: DataOutputStream) {
        val length = input.readInt()
        val code = input.readInt()
        if (code == SSL_REQUEST) {
            output.writeByte(SSL_DENIED)
            output.flush()
            val startupLength = input.readInt()
            input.skipNBytes((startupLength - Int.SIZE_BYTES).toLong())
        } else {
            input.skipNBytes((length - 2 * Int.SIZE_BYTES).toLong())
        }
    }

    private fun writeHandshake(output: DataOutputStream) {
        output.writeByte('R'.code)
        output.writeInt(AUTH_OK_LENGTH)
        output.writeInt(0)
        parameterStatus(output, "server_version", "14.5")
        parameterStatus(output, "client_encoding", "UTF8")
        parameterStatus(output, "standard_conforming_strings", "on")
        parameterStatus(output, "integer_datetimes", "on")
        parameterStatus(output, "TimeZone", "UTC")
        output.writeByte('K'.code)
        output.writeInt(BACKEND_KEY_LENGTH)
        output.writeInt(BACKEND_PID)
        output.writeInt(BACKEND_SECRET)
        output.writeByte('Z'.code)
        output.writeInt(READY_LENGTH)
        output.writeByte('I'.code)
        output.flush()
    }

    private fun parameterStatus(output: DataOutputStream, key: String, value: String) {
        val payload = key.toByteArray() + NUL + value.toByteArray() + NUL
        output.writeByte('S'.code)
        output.writeInt(Int.SIZE_BYTES + payload.size)
        output.write(payload)
    }

    private fun writeErrorResponse(output: DataOutputStream) {
        val fields = field('S', "FATAL") +
            field('V', "FATAL") +
            field('C', sqlState) +
            field('M', "fake postgres failure $sqlState") +
            NUL
        output.writeByte('E'.code)
        output.writeInt(Int.SIZE_BYTES + fields.size)
        output.write(fields)
        output.flush()
    }

    private fun field(type: Char, value: String): ByteArray =
        byteArrayOf(type.code.toByte()) + value.toByteArray() + NUL

    override fun close() {
        server.close()
    }

    companion object {
        private const val SSL_REQUEST = 80877103
        private const val SSL_DENIED = 'N'.code
        private const val AUTH_OK_LENGTH = 8
        private const val BACKEND_KEY_LENGTH = 12
        private const val BACKEND_PID = 42
        private const val BACKEND_SECRET = 42
        private const val READY_LENGTH = 5
        private val NUL = byteArrayOf(0)

        fun failingAtStartup(sqlState: String): FakePostgres =
            FakePostgres(Mode.STARTUP_ERROR, sqlState)

        fun failingOnFirstStatement(sqlState: String): FakePostgres =
            FakePostgres(Mode.STATEMENT_ERROR, sqlState)

        fun closingOnFirstStatement(): FakePostgres =
            FakePostgres(Mode.STATEMENT_CLOSE, "")
    }
}
