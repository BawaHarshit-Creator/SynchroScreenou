package com.example.synchroscreenou.network.client

import android.content.Context
import android.util.Log
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

class TcpClient {

    private var socket: Socket? = null

    @Volatile
    private var isListening = false

    fun connect(
        ipAddress: String,
        port: Int,
        onResult: (Boolean, String?) -> Unit
    ) {
        Thread {
            try {
                val clientSocket = Socket()

                clientSocket.connect(
                    InetSocketAddress(ipAddress, port),
                    5000
                )

                socket = clientSocket

                Log.d("TcpClient", "Connected to $ipAddress:$port")

                onResult(true, null)

            } catch (e: IOException) {
                Log.e("TcpClient", "Connection failed", e)
                onResult(false, e.message)
            }
        }.start()
    }

    // Continuously listen for messages from the host.
    fun startListening(
        context: Context,
        onVideoReceived: (File?, String?) -> Unit,
        onPlaybackCommand: (String, Long?) -> Unit
    ) {
        if (isListening) return

        isListening = true

        Thread {
            try {
                val clientSocket = socket
                    ?: throw IOException("Not connected to host")

                val input = DataInputStream(
                    BufferedInputStream(clientSocket.getInputStream())
                )

                while (isListening && !clientSocket.isClosed) {

                    // Read incoming message type.
                    val messageType = input.readUTF()

                    when (messageType) {

                        "VIDEO" -> {

                            val fileSize = input.readLong()
                            val fileName = input.readUTF()

                            if (fileSize <= 0L) {
                                throw IOException("Invalid video file size")
                            }

                            if (fileSize > context.cacheDir.usableSpace) {
                                throw IOException("Not enough storage space")
                            }

                            val safeName = File(fileName).name

                            val videoFile = File(
                                context.cacheDir,
                                "received_$safeName"
                            )

                            FileOutputStream(videoFile).use { output ->

                                val buffer = ByteArray(64 * 1024)
                                var remaining = fileSize

                                while (remaining > 0) {

                                    val bytesToRead = minOf(
                                        buffer.size.toLong(),
                                        remaining
                                    ).toInt()

                                    val bytesRead = input.read(
                                        buffer,
                                        0,
                                        bytesToRead
                                    )

                                    if (bytesRead == -1) {
                                        throw IOException(
                                            "Video transfer interrupted"
                                        )
                                    }

                                    output.write(buffer, 0, bytesRead)

                                    remaining -= bytesRead
                                }
                            }

                            Log.d(
                                "TcpClient",
                                "Video received: ${videoFile.absolutePath}"
                            )

                            onVideoReceived(videoFile, null)
                        }

                        "PLAY" -> {
                            Log.d("TcpClient", "PLAY command received")
                            onPlaybackCommand("PLAY", null)
                        }

                        "PAUSE" -> {
                            Log.d("TcpClient", "PAUSE command received")
                            onPlaybackCommand("PAUSE", null)
                        }

                        "SEEK" -> {
                            val position = input.readLong()

                            Log.d(
                                "TcpClient",
                                "SEEK command received: $position"
                            )

                            onPlaybackCommand("SEEK", position)
                        }

                        else -> {
                            Log.e(
                                "TcpClient",
                                "Unknown message: $messageType"
                            )
                        }
                    }
                }

            } catch (e: Exception) {
                if (isListening) {
                    Log.e("TcpClient", "Listening error", e)
                }
            } finally {
                isListening = false
            }

        }.start()
    }

    fun disconnect() {
        isListening = false

        try {
            socket?.close()
            socket = null

            Log.d("TcpClient", "Disconnected")

        } catch (e: IOException) {
            Log.e("TcpClient", "Disconnect error", e)
        }
    }
}