package com.example.synchroscreenou.network.server

import android.util.Log
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import android.os.SystemClock
import java.io.DataInputStream
import java.io.BufferedInputStream
class TcpServer {

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var isRunning = false

    private val connectedClients = mutableSetOf<Socket>()

    fun start(
        onServerStarted: (Int) -> Unit,
        onClientCountChanged: (Int) -> Unit
    ) {
        if (isRunning) return

        isRunning = true

        Thread {
            try {
                val server = ServerSocket(0)
                serverSocket = server

                Log.d("TcpServer", "Server started on port: ${server.localPort}")

                onServerStarted(server.localPort)

                while (isRunning) {
                    val client = server.accept()

                    val clientCount = synchronized(connectedClients) {
                        connectedClients.add(client)
                        connectedClients.size
                    }

                    onClientCountChanged(clientCount)

                    Log.d(
                        "TcpServer",
                        "Client connected: ${client.inetAddress.hostAddress}"
                    )

                    Thread {
                        handleClient(client, onClientCountChanged)
                    }.start()
                }

            } catch (e: SocketException) {
                if (isRunning) {
                    Log.e("TcpServer", "Socket error", e)
                }
            } catch (e: IOException) {
                Log.e("TcpServer", "Server error", e)
            } finally {
                isRunning = false
            }
        }.start()
    }

    private fun handleClient(
        client: Socket,
        onClientCountChanged: (Int) -> Unit
    ) {
        try {
            val input = DataInputStream(
                BufferedInputStream(client.getInputStream())
            )

            while (isRunning && !client.isClosed) {

                val messageType = input.readUTF()

                when (messageType) {

                    "SYNC_REQUEST" -> {

                        // Time when host receives the request
                        val hostReceiveTime =
                            SystemClock.elapsedRealtimeNanos()

                        // Read client's sending timestamp
                        val clientSendTime = input.readLong()

                        // Time when host sends the response
                        val hostSendTime =
                            SystemClock.elapsedRealtimeNanos()

                        synchronized(client) {

                            val output = DataOutputStream(
                                client.getOutputStream()
                            )

                            output.writeUTF("SYNC_RESPONSE")
                            output.writeLong(clientSendTime)
                            output.writeLong(hostReceiveTime)
                            output.writeLong(hostSendTime)
                            output.flush()

                        }

                        Log.d("TcpServer", "Clock sync response sent")
                    }
                }
            }

        } catch (e: IOException) {
            Log.d("TcpServer", "Client disconnected")
        } finally {

            val clientCount = synchronized(connectedClients) {
                connectedClients.remove(client)
                connectedClients.size
            }

            onClientCountChanged(clientCount)

            try {
                client.close()
            } catch (_: IOException) {
            }

            Log.d("TcpServer", "Client connection closed")
        }
    }

    // Send a video to all connected phones.
    fun sendVideo(
        videoFile: File,
        onResult: (String, Boolean) -> Unit
    ) {
        if (!videoFile.exists() || !videoFile.isFile) {
            Log.e("TcpServer", "Video file does not exist")
            return
        }

        val clients = synchronized(connectedClients) {
            connectedClients.toList()
        }

        if (clients.isEmpty()) {
            Log.d("TcpServer", "No connected phones")
            return
        }

        clients.forEach { client ->

            Thread {
                val clientAddress =
                    client.inetAddress.hostAddress ?: "Unknown"

                try {
                    synchronized(client) {

                        val output = DataOutputStream(
                            BufferedOutputStream(client.getOutputStream())
                        )

                        // Message type
                        output.writeUTF("VIDEO")

                        // Video information
                        output.writeLong(videoFile.length())
                        output.writeUTF(videoFile.name)

                        // Send video in chunks
                        FileInputStream(videoFile).use { input ->

                            val buffer = ByteArray(64 * 1024)

                            while (true) {
                                val bytesRead = input.read(buffer)

                                if (bytesRead == -1) break

                                output.write(buffer, 0, bytesRead)
                            }
                        }

                        output.flush()
                    }

                    Log.d(
                        "TcpServer",
                        "Video sent successfully to $clientAddress"
                    )

                    onResult(clientAddress, true)

                } catch (e: IOException) {

                    Log.e(
                        "TcpServer",
                        "Video transfer failed for $clientAddress",
                        e
                    )

                    onResult(clientAddress, false)
                }

            }.start()
        }
    }

    // Send PLAY or PAUSE to all connected phones.
    fun sendPlaybackCommand(command: String) {

        if (command != "PLAY" && command != "PAUSE") {
            Log.e("TcpServer", "Invalid playback command: $command")
            return
        }

        sendCommandToClients(command)
    }

    // Send a SEEK command with a timestamp in milliseconds.
    fun sendSeekCommand(position: Long) {

        sendCommandToClients("SEEK", position)
    }

    private fun sendCommandToClients(
        command: String,
        position: Long? = null
    ) {

        val clients = synchronized(connectedClients) {
            connectedClients.toList()
        }

        clients.forEach { client ->

            Thread {
                try {
                    synchronized(client) {

                        val output = DataOutputStream(
                            client.getOutputStream()
                        )

                        output.writeUTF(command)

                        if (command == "SEEK") {
                            output.writeLong(position ?: 0L)
                        }

                        output.flush()
                    }

                    Log.d(
                        "TcpServer",
                        "$command sent to ${client.inetAddress.hostAddress}"
                    )

                } catch (e: IOException) {
                    Log.e("TcpServer", "Command sending failed", e)
                }
            }.start()
        }
    }

    fun stop() {

        isRunning = false

        try {
            serverSocket?.close()
        } catch (_: IOException) {
        }

        synchronized(connectedClients) {
            connectedClients.forEach {
                try {
                    it.close()
                } catch (_: IOException) {
                }
            }

            connectedClients.clear()
        }

        serverSocket = null

        Log.d("TcpServer", "Server stopped")
    }
}