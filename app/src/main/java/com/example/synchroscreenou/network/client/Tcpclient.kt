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

    @Volatile
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
                clientSocket.tcpNoDelay = true
                clientSocket.connect(InetSocketAddress(ipAddress, port), 5000)
                socket = clientSocket

                Log.d("TcpClient", "Connected to $ipAddress:$port")
                onResult(true, null)
            } catch (e: IOException) {
                Log.e("TcpClient", "Connection failed", e)
                onResult(false, e.message)
            }
        }.start()
    }

    fun startListening(
        context: Context,
        onVideoReceived: (File?, String?) -> Unit,
        onPlaybackCommand: (String, Long?) -> Unit
    ) {
        val clientSocket = socket
        if (clientSocket == null || !clientSocket.isConnected || clientSocket.isClosed) {
            onVideoReceived(null, "Not connected to host")
            return
        }
        if (isListening) return
        isListening = true

        Thread {
            try {
                val input = DataInputStream(
                    BufferedInputStream(clientSocket.getInputStream())
                )

                while (isListening && !clientSocket.isClosed) {
                    when (val messageType = input.readUTF()) {
                        "VIDEO" -> {
                            val fileSize = input.readLong()
                            val fileName = input.readUTF()

                            if (fileSize <= 0L) {
                                throw IOException("Invalid video file size: $fileSize")
                            }
                            if (fileSize > context.cacheDir.usableSpace) {
                                throw IOException("Not enough storage space for video")
                            }

                            val safeName = File(fileName).name
                            val uniqueName = "received_${System.currentTimeMillis()}_$safeName"
                            val finalFile = File(context.cacheDir, uniqueName)
                            val temporaryFile = File(context.cacheDir, "$uniqueName.part")

                            try {
                                FileOutputStream(temporaryFile).use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    var remaining = fileSize

                                    while (remaining > 0L) {
                                        val requested = minOf(buffer.size.toLong(), remaining).toInt()
                                        val bytesRead = input.read(buffer, 0, requested)
                                        if (bytesRead < 0) {
                                            throw IOException("Video transfer interrupted; $remaining bytes missing")
                                        }
                                        if (bytesRead == 0) continue

                                        output.write(buffer, 0, bytesRead)
                                        remaining -= bytesRead
                                    }
                                    output.fd.sync()
                                    output.flush()
                                }

                                if (temporaryFile.length() != fileSize) {
                                    throw IOException(
                                        "Incomplete video: received ${temporaryFile.length()} of $fileSize bytes"
                                    )
                                }

                                if (finalFile.exists() && !finalFile.delete()) {
                                    throw IOException("Could not replace previous received video")
                                }
                                if (!temporaryFile.renameTo(finalFile)) {
                                    throw IOException("Could not finalize received video file")
                                }

                                Log.d(
                                    "TcpClient",
                                    "Video received: ${finalFile.absolutePath}, ${finalFile.length()} bytes"
                                )
                                onVideoReceived(finalFile, null)
                            } catch (e: Exception) {
                                temporaryFile.delete()
                                throw e
                            }
                        }

                        "PLAY" -> onPlaybackCommand("PLAY", null)
                        "PAUSE" -> onPlaybackCommand("PAUSE", null)
                        "SEEK" -> {
                            val position = input.readLong()
                            onPlaybackCommand("SEEK", position)
                        }

                        else -> {
                            Log.e("TcpClient", "Unknown host message: $messageType")
                            throw IOException("Unsupported host message: $messageType")
                        }
                    }
                }
            } catch (e: Exception) {
                if (isListening) {
                    Log.e("TcpClient", "Listening stopped", e)
                    onVideoReceived(null, e.message ?: "Connection error")
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
        } catch (e: IOException) {
            Log.e("TcpClient", "Disconnect error", e)
        } finally {
            socket = null
            Log.d("TcpClient", "Disconnected")
        }
    }
}
