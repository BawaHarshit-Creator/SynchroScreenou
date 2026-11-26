package com.example.synchroscreenou.ui.screens

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File

@Composable
fun VideoPlayer(
    videoFile: File,
    tileIndex: Int = 0,
    isMuted: Boolean = false,
    onPlayerReady: (ExoPlayer) -> Unit = {},
    onPlaybackCommand: ((String, Long?) -> Unit)? = null,
    showController: Boolean = false
) {
    val context = LocalContext.current
    var isFullscreen by remember { mutableStateOf(false) }

    val latestOnPlayerReady by rememberUpdatedState(onPlayerReady)
    val latestOnPlaybackCommand by rememberUpdatedState(onPlaybackCommand)

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = false
            repeatMode = Player.REPEAT_MODE_ONE
            volume = if (isMuted) 0f else 1f
        }
    }

    DisposableEffect(player, isMuted) {
        player.volume = if (isMuted) 0f else {
            1f
        }
        onDispose {}
    }

    DisposableEffect(player) {
        var readyReported = false

        val listener = object : Player.Listener {

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && !readyReported) {
                    readyReported = true
                    latestOnPlayerReady(player)
                }
            }

            override fun onPlayWhenReadyChanged(
                playWhenReady: Boolean,
                reason: Int
            ) {
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                    latestOnPlaybackCommand?.invoke(
                        if (playWhenReady) "PLAY" else "PAUSE",
                        null
                    )
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    latestOnPlaybackCommand?.invoke(
                        "SEEK",
                        newPosition.positionMs
                    )
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("VideoPlayer", "Playback error: ${error.message}", error)
            }
        }

        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(videoFile) {
        if (videoFile.exists() && videoFile.length() > 0L) {
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(videoFile)))
            player.prepare()
        } else {
            Log.e("VideoPlayer", "Video file missing or empty")
        }
    }

    val transformOrigin = when (tileIndex) {
        1 -> TransformOrigin(1f, 0f) // Top-Right
        2 -> TransformOrigin(0f, 1f) // Bottom-Left
        3 -> TransformOrigin(1f, 1f) // Bottom-Right
        else -> TransformOrigin(0f, 0f) // Top-Left (0)
    }

    @Composable
    fun PlayerContent(modifier: Modifier) {
        Box(
            modifier = modifier.clipToBounds()
        ) {
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        this.player = player
                        useController = showController
                        setFullscreenButtonClickListener {
                            isFullscreen = !isFullscreen
                        }
                    }
                },
                update = { playerView ->
                    playerView.player = player
                    playerView.useController = showController
                    playerView.setFullscreenButtonClickListener {
                        isFullscreen = !isFullscreen
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = 2f,
                        scaleY = 2f,
                        transformOrigin = transformOrigin
                    )
            )
        }
    }

    if (isFullscreen) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                PlayerContent(
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    } else {
        PlayerContent(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
        )
    }
}
