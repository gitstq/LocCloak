package com.locctl.app.adb

import android.os.Build
import android.util.Log
import com.locctl.app.adb.AdbProtocol.ADB_AUTH_RSAPUBLICKEY
import com.locctl.app.adb.AdbProtocol.ADB_AUTH_SIGNATURE
import com.locctl.app.adb.AdbProtocol.ADB_AUTH_TOKEN
import com.locctl.app.adb.AdbProtocol.A_AUTH
import com.locctl.app.adb.AdbProtocol.A_CLSE
import com.locctl.app.adb.AdbProtocol.A_CNXN
import com.locctl.app.adb.AdbProtocol.A_MAXDATA
import com.locctl.app.adb.AdbProtocol.A_OKAY
import com.locctl.app.adb.AdbProtocol.A_OPEN
import com.locctl.app.adb.AdbProtocol.A_STLS
import com.locctl.app.adb.AdbProtocol.A_STLS_VERSION
import com.locctl.app.adb.AdbProtocol.A_VERSION
import com.locctl.app.adb.AdbProtocol.A_WRTE
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.net.ssl.SSLSocket

private const val TAG = "AdbClient"

class AdbClient(private val host: String, private val port: Int, private val key: AdbKey) : Closeable {

    private lateinit var socket: Socket
    private lateinit var plainInputStream: DataInputStream
    private lateinit var plainOutputStream: DataOutputStream

    private var useTls = false

    private lateinit var tlsSocket: SSLSocket
    private lateinit var tlsInputStream: DataInputStream
    private lateinit var tlsOutputStream: DataOutputStream

    private val inputStream get() = if (useTls) tlsInputStream else plainInputStream
    private val outputStream get() = if (useTls) tlsOutputStream else plainOutputStream

    fun connect() {
        socket = Socket(host, port)
        socket.tcpNoDelay = true
        socket.soTimeout = 10000
        plainInputStream = DataInputStream(socket.getInputStream())
        plainOutputStream = DataOutputStream(socket.getOutputStream())

        write(A_CNXN, A_VERSION, A_MAXDATA, "host::")

        var message = read()
        if (message.command == A_STLS) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                error("Connect to adb with TLS is not supported before Android 10")
            }
            write(A_STLS, A_STLS_VERSION, 0)

            val sslContext = key.sslContext
            tlsSocket = sslContext.socketFactory.createSocket(socket, host, port, true) as SSLSocket
            tlsSocket.soTimeout = 10000
            tlsSocket.startHandshake()
            Log.d(TAG, "Handshake succeeded.")

            tlsInputStream = DataInputStream(tlsSocket.inputStream)
            tlsOutputStream = DataOutputStream(tlsSocket.outputStream)
            useTls = true

            message = read()
        } else if (message.command == A_AUTH) {
            if (message.command != A_AUTH && message.arg0 != ADB_AUTH_TOKEN) error("not A_AUTH ADB_AUTH_TOKEN")
            write(A_AUTH, ADB_AUTH_SIGNATURE, 0, key.sign(message.data))

            message = read()
            if (message.command != A_CNXN) {
                write(A_AUTH, ADB_AUTH_RSAPUBLICKEY, 0, key.adbPublicKey)
                message = read()
            }
        }

        if (message.command != A_CNXN) error("not A_CNXN")
    }

    private val localIdGen = java.util.concurrent.atomic.AtomicInteger(0)

    fun shellCommand(command: String, listener: ((ByteArray) -> Unit)?) {
        val localId = localIdGen.incrementAndGet()
        write(A_OPEN, localId, 0, "shell:$command")

        var message: AdbMessage
        // 过滤丢弃旧 stream 的残留包（arg1 != localId）
        while (true) {
            message = read()
            if (message.arg1 == localId) {
                break
            }
            Log.w(TAG, "丢弃过期流数据包: cmd=0x${Integer.toHexString(message.command)}, arg0=${message.arg0}, arg1=${message.arg1}, expected=$localId")
        }

        when (message.command) {
            A_OKAY -> {
                val remoteId = message.arg0
                while (true) {
                    message = read()
                    if (message.arg1 != localId) {
                        Log.w(TAG, "丢弃非当前流的数据包: cmd=0x${Integer.toHexString(message.command)}, arg1=${message.arg1}, expected=$localId")
                        continue
                    }
                    if (message.command == A_WRTE) {
                        if (message.data_length > 0 && message.data != null) {
                            listener?.invoke(message.data)
                        }
                        write(A_OKAY, localId, remoteId)
                    } else if (message.command == A_CLSE) {
                        write(A_CLSE, localId, remoteId)
                        break
                    } else {
                        Log.w(TAG, "收到非预期 ADB 包 (cmd=0x${Integer.toHexString(message.command)})，跳过该流")
                        break
                    }
                }
            }
            A_CLSE -> {
                val remoteId = message.arg0
                write(A_CLSE, localId, remoteId)
            }
            else -> {
                Log.w(TAG, "A_OPEN 收到非预期响应: cmd=0x${Integer.toHexString(message.command)}")
            }
        }
    }

    fun executeCommand(command: String): String {
        val sb = java.lang.StringBuilder()
        shellCommand(command) { sb.append(String(it)) }
        return sb.toString()
    }

    private fun write(command: Int, arg0: Int, arg1: Int, data: ByteArray? = null) = write(AdbMessage(command, arg0, arg1, data))

    private fun write(command: Int, arg0: Int, arg1: Int, data: String) = write(AdbMessage(command, arg0, arg1, data))

    private fun write(message: AdbMessage) {
        outputStream.write(message.toByteArray())
        outputStream.flush()
        Log.d(TAG, "write ${message.toStringShort()}")
    }

    private fun read(): AdbMessage {
        val buffer = ByteBuffer.allocate(AdbMessage.HEADER_LENGTH).order(ByteOrder.LITTLE_ENDIAN)

        inputStream.readFully(buffer.array(), 0, 24)

        val command = buffer.int
        val arg0 = buffer.int
        val arg1 = buffer.int
        val dataLength = buffer.int
        val checksum = buffer.int
        val magic = buffer.int
        val data: ByteArray?
        if (dataLength >= 0) {
            data = ByteArray(dataLength)
            inputStream.readFully(data, 0, dataLength)
        } else {
            data = null
        }
        val message = AdbMessage(command, arg0, arg1, dataLength, checksum, magic, data)
        message.validateOrThrow()
        Log.d(TAG, "read ${message.toStringShort()}")
        return message
    }

    override fun close() {
        try {
            plainInputStream.close()
        } catch (e: Throwable) {
        }
        try {
            plainOutputStream.close()
        } catch (e: Throwable) {
        }
        try {
            socket.close()
        } catch (e: Exception) {
        }

        if (useTls) {
            try {
                tlsInputStream.close()
            } catch (e: Throwable) {
            }
            try {
                tlsOutputStream.close()
            } catch (e: Throwable) {
            }
            try {
                tlsSocket.close()
            } catch (e: Exception) {
            }
        }
    }
}

