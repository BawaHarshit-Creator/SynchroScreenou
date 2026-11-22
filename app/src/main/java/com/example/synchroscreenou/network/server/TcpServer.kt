package com.example.synchroscreenou.network.server

import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

class TcpServer {

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var isRunning = false

    // Each connected client has exactly one output stream. All writes to that
    // stream synchronize on the stream itself to prevent packet interleaving.
    private val clientStreams = mutableMapOf<Socket, DataOutputStream>()

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
                    client.tcpNoDelay = true

                    val output = DataOutputStream(
                        BufferedOutputStream(client.getOutputStream())
                    )

                    val count = synchronized(clientStreams) {
                        clientStreams[client] = output
                        clientStreams.size
                    }
                    onClientCountChanged(count)

                    Log.d(
                        "TcpServer",
                        "Client connected: ${client.inetAddress.hostAddress}"
                    )

                    Thread {
                        handleClient(client, onClientCountChanged)
                    }.start()
                }
            } catch (e: SocketException) {
                if (isRunning) Log.e("TcpServer", "Socket error", e)
            } catch (e: IOException) {
                if (isRunning) Log.e("TcpServer", "Server error", e)
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
                Log.w("TcpServer", "Unexpected message from client: $messageType")
            }
        } catch (e: IOException) {
            if (isRunning) Log.d("TcpServer", "Client disconnected: ${e.message}")
        } finally {
            val count = synchronized(clientStreams) {
                clientStreams.remove(client)
                clientStreams.size
            }
            onClientCountChanged(count)

            try {
                client.close()
            } catch (_: IOException) {
            }
            Log.d("TcpServer", "Client connection closed")
        }
    }

    fun sendVideo(
        videoFile: File,
        onResult: (String, Boolean) -> Unit
    ) {
        if (!videoFile.exists() || !videoFile.isFile || videoFile.length() <= 0L) {
            Log.e("TcpServer", "Video file is missing or empty: ${videoFile.absolutePath}")
            return
        }

        val entries = synchronized(clientStreams) {
            clientStreams.entries.toList()
        }

        if (entries.isEmpty()) {
            Log.d("TcpServer", "No connected phones")
            return
        }

        entries.forEach { (client, output) ->
            Thread {
                val address = client.inetAddress.hostAddress ?: "Unknown"
                try {
                    synchronized(output) {
                        output.writeUTF("VIDEO")
                        output.writeLong(videoFile.length())
                        output.writeUTF(videoFile.name)

                        FileInputStream(videoFile).use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                            }
                        }
                        output.flush()
                    }

                    Log.d("TcpServer", "Video sent to $address")
                    onResult(address, true)
                } catch (e: IOException) {
                    Log.e("TcpServer", "Video transfer failed for $address", e)
                    onResult(address, false)
                }
            }.start()
        }
    }

    fun sendPlaybackCommand(command: String) {
        if (command != "PLAY" && command != "PAUSE") {
            Log.e("TcpServer", "Invalid playback command: $command")
            return
        }
        sendCommandToClients(command)
    }

    fun sendSeekCommand(position: Long) {
        sendCommandToClients("SEEK", position)
    }

    private fun sendCommandToClients(
        command: String,
        position: Long? = null
    ) {
        val entries = synchronized(clientStreams) {
            clientStreams.entries.toList()
        }

        entries.forEach { (client, output) ->
            Thread {
                try {
                    synchronized(output) {
                        output.writeUTF(command)
                        if (command == "SEEK") output.writeLong(position ?: 0L)
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

        synchronized(clientStreams) {
            clientStreams.forEach { (client, output) ->
                try {
                    output.close()
                } catch (_: IOException) {
                }
                try {
                    client.close()
                } catch (_: IOException) {
                }
            }
            clientStreams.clear()
        }

        serverSocket = null
        Log.d("TcpServer", "Server stopped")
    }
}
