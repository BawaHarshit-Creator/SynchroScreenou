package com.example.synchroscreenou.ui.screens

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.synchroscreenou.network.discovery.DiscoveredDisplay
import com.example.synchroscreenou.network.discovery.NetworkDiscoveryManager
import com.example.synchroscreenou.network.client.TcpClient
import java.io.File
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.Player
@Composable
fun JoinDisplayScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val tcpClient = remember { TcpClient() }

    var connectionStatus by remember {
        mutableStateOf("Not connected")
    }

    var isConnecting by remember {
        mutableStateOf(false)
    }

    var receivingStatus by remember {
        mutableStateOf("No video received")
    }

    var receivedVideoFile by remember {
        mutableStateOf<File?>(null)
    }
    var exoPlayer by remember {
        mutableStateOf<ExoPlayer?>(null)
    }
    val pendingPlaybackCommands = remember {
        mutableStateListOf<Pair<String, Long?>>()
    }
    val discoveredDisplays = remember {
        mutableStateListOf<DiscoveredDisplay>()
    }

    val discoveryManager = remember {
        NetworkDiscoveryManager(context) { display ->
            mainHandler.post {
                if (discoveredDisplays.none {
                        it.name == display.name &&
                                it.ipAddress == display.ipAddress
                    }) {
                    discoveredDisplays.add(display)
                }
            }
        }
    }

    var isSearching by remember {
        mutableStateOf(false)
    }

    DisposableEffect(Unit) {
        onDispose {
            discoveryManager.stopDiscovery()
            tcpClient.disconnect()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "Join Display",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Search Session",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {

                Text(
                    if (isSearching) "Status: Searching..."
                    else "Status: Idle"
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Connection: $connectionStatus",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Video: $receivingStatus",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (discoveredDisplays.isEmpty()) {

                    Text(
                        if (isSearching) {
                            "Searching for nearby displays..."
                        } else {
                            "No displays found yet."
                        }
                    )

                } else {

                    Text("Discovered displays: ${discoveredDisplays.size}")

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                    ) {
                        LazyColumn {

                            items(discoveredDisplays) { display ->

                                Card(
                                    onClick = {
                                        if (!isConnecting) {

                                            isConnecting = true
                                            connectionStatus = "Connecting..."

                                            tcpClient.connect(
                                                ipAddress = display.ipAddress,
                                                port = display.port
                                            ) { success, error ->

                                                mainHandler.post {

                                                    isConnecting = false

                                                    if (success) {

                                                        connectionStatus =
                                                            "Connected to ${display.name}"

                                                        receivingStatus =
                                                            "Waiting for video..."

                                                        Toast.makeText(
                                                            context,
                                                            "Connected successfully!",
                                                            Toast.LENGTH_SHORT
                                                        ).show()

                                                        tcpClient.startListening(
                                                            context = context,

                                                            onVideoReceived = { file, receiveError ->

                                                                mainHandler.post {

                                                                    if (file != null) {
                                                                        receivedVideoFile = file
                                                                        exoPlayer = null
                                                                         receivingStatus = "Video received successfully!"
                                                                    } else {
                                                                        receivingStatus = "Receiving failed: $receiveError"
                                                                    }
                                                                }
                                                            },

                                                            onPlaybackCommand = { command, position ->

                                                                mainHandler.post {

                                                                    val readyPlayer = exoPlayer
                                                                    if (
                                                                        readyPlayer != null &&
                                                                        readyPlayer.playbackState == Player.STATE_READY
                                                                    ) {
                                                                        applyPlaybackCommand(readyPlayer, command, position)
                                                                    } else {
                                                                        pendingPlaybackCommands.add(command to position)
                                                                    }
                                                                }
                                                            }
                                                        )

                                                    } else {

                                                        connectionStatus =
                                                            "Connection failed: $error"

                                                        Toast.makeText(
                                                            context,
                                                            "Connection failed",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        Text(
                                            text = display.name,
                                            style = MaterialTheme.typography.titleMedium
                                        )

                                        Spacer(
                                            modifier = Modifier.height(2.dp)
                                        )

                                        Text(
                                            text = "IP: ${display.ipAddress}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )

                                        Text(
                                            text = "Port: ${display.port}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (isSearching) {

                            discoveryManager.stopDiscovery()
                            isSearching = false

                        } else {

                            discoveredDisplays.clear()
                            discoveryManager.startDiscovery()
                            isSearching = true
                        }
                    }
                ) {
                    Text(
                        if (isSearching) "Stop Searching"
                        else "Search Devices"
                    )
                }
            }
        }

        // Show the video player after receiving a video.
        receivedVideoFile?.let { videoFile ->

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Received Video",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(12.dp))

            VideoPlayer(
                videoFile = videoFile,
                onPlayerReady = { player ->
                    exoPlayer = player
                    pendingPlaybackCommands.toList().forEach { (command, position) ->
                        applyPlaybackCommand(player, command, position)
                    }
                    pendingPlaybackCommands.clear()
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                discoveryManager.stopDiscovery()
                tcpClient.disconnect()
                onBack()
            }
        ) {
            Text("Back")
        }
    }
}

private fun applyPlaybackCommand(
    player: ExoPlayer,
    command: String,
    position: Long?
) {
    when (command) {
        "PLAY" -> player.play()
        "PAUSE" -> player.pause()
        "SEEK" -> player.seekTo(position ?: 0L)
    }
}
