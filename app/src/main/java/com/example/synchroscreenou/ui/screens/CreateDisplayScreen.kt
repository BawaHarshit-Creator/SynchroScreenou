package com.example.synchroscreenou.ui.screens

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.synchroscreenou.network.discovery.NetworkDiscoveryManager
import com.example.synchroscreenou.network.server.TcpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.media3.exoplayer.ExoPlayer
@Composable
fun CreateDisplayScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var videoDetails by remember { mutableStateOf("No video selected") }
    var transferStatus by remember { mutableStateOf("") }
    var isStarted by remember { mutableStateOf(false) }
    var connectedDevices by remember { mutableIntStateOf(0) }
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }

    var videoDuration by remember { mutableLongStateOf(0L) }
    var seekPosition by remember { mutableFloatStateOf(0f) }
    var isVideoSent by remember { mutableStateOf(false) }
    var hostVideoFile by remember {
        mutableStateOf<File?>(null)
    }

    var hostPlayer by remember {
        mutableStateOf<ExoPlayer?>(null)
    }
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            }

            selectedVideoUri = uri
            isVideoSent = false
            transferStatus = ""
            seekPosition = 0f
        }
    }

    val tcpServer = remember { TcpServer() }
    val discoveryManager = remember { NetworkDiscoveryManager(context) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    // Read video metadata
    LaunchedEffect(selectedVideoUri) {
        val uri = selectedVideoUri ?: return@LaunchedEffect

        videoDetails = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()

            try {
                retriever.setDataSource(context, uri)

                val duration = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLongOrNull() ?: 0L

                val width = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                )

                val height = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                )

                videoDuration = duration

                "Duration: ${duration / 1000} seconds\n" +
                        "Resolution: ${width ?: "Unknown"} × ${height ?: "Unknown"}"

            } catch (e: Exception) {
                videoDuration = 0L
                "Unable to read video details"
            } finally {
                retriever.release()
            }
        }
    }

    fun stopDisplay() {
        tcpServer.stop()
        discoveryManager.stopAdvertising()
        connectedDevices = 0
        isStarted = false
        isVideoSent = false
    }

    DisposableEffect(Unit) {
        onDispose {
            tcpServer.stop()
            discoveryManager.stopAdvertising()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "Create Display",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Display Session",
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
                    if (isStarted) "Status: Active"
                    else "Status: Not started"
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text("Connected devices: $connectedDevices")

                Spacer(modifier = Modifier.height(8.dp))

                Text("Waiting for other phones...")

                Spacer(modifier = Modifier.height(16.dp))

                // Select video
                Button(
                    onClick = {
                        videoPicker.launch(arrayOf("video/*"))
                    }
                ) {
                    Text("Select Video")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(videoDetails)

                // Send video
                Button(
                    onClick = {
                        val uri = selectedVideoUri ?: return@Button

                        Thread {
                            try {
                                val videoFile = File(
                                    context.cacheDir,
                                    "synchroscreen_video"
                                )

                                context.contentResolver
                                    .openInputStream(uri)
                                    ?.use { input ->
                                        videoFile.outputStream().use { output ->
                                            input.copyTo(output)
                                        }
                                    } ?: throw Exception(
                                    "Cannot open selected video"
                                )
                                mainHandler.post {
                                    hostVideoFile = videoFile
                                }
                                tcpServer.sendVideo(videoFile) { address, success ->
                                    mainHandler.post {
                                        transferStatus =
                                            if (success) {
                                                isVideoSent = true
                                                "Video sent to $address"
                                            } else {
                                                "Transfer failed for $address"
                                            }
                                    }
                                }

                            } catch (e: Exception) {
                                mainHandler.post {
                                    transferStatus = "Error: ${e.message}"
                                }
                            }
                        }.start()
                    },
                    enabled = selectedVideoUri != null && connectedDevices > 0
                ) {
                    Text("Send Video to Connected Phones")
                }

                Text(transferStatus)
                hostVideoFile?.let { file ->
                    VideoPlayer(
                        videoFile = file,
                        onPlayerReady = { player ->
                            hostPlayer = player
                        }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Playback controls
                Text(
                    text = "Playback Controls",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            hostPlayer?.play()
                            tcpServer.sendPlaybackCommand("PLAY")
                        },
                        enabled = isVideoSent && connectedDevices > 0,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Play All")
                    }

                    Button(
                        onClick = {
                            hostPlayer?.pause()
                            tcpServer.sendPlaybackCommand("PAUSE")
                        },
                        enabled = isVideoSent && connectedDevices > 0,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Pause All")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Seek: ${(seekPosition / 1000).toInt()} seconds")

                Slider(
                    value = seekPosition,
                    onValueChange = { seekPosition = it },
                    valueRange = 0f..videoDuration.toFloat().coerceAtLeast(1f),
                    onValueChangeFinished = {
                        hostPlayer?.seekTo(seekPosition.toLong())
                        tcpServer.sendSeekCommand(seekPosition.toLong())
                    },
                    enabled = isVideoSent &&
                            connectedDevices > 0 &&
                            videoDuration > 0
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Start / Stop Display
                Button(
                    onClick = {
                        if (isStarted) {
                            stopDisplay()
                        } else {
                            tcpServer.start(
                                onServerStarted = { port ->
                                    discoveryManager.startAdvertising(port)

                                    mainHandler.post {
                                        isStarted = true
                                    }
                                },
                                onClientCountChanged = { count ->
                                    mainHandler.post {
                                        connectedDevices = count
                                    }
                                }
                            )
                        }
                    }
                ) {
                    Text(
                        if (isStarted) "Stop Display"
                        else "Start Display"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                stopDisplay()
                onBack()
            }
        ) {
            Text("Back")
        }
    }
}