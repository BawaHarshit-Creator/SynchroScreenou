package com.example.synchroscreenou.ui.screens

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File

@Composable
fun VideoPlayer(
    videoFile: File,
    onPlayerReady: (ExoPlayer) -> Unit = {}
) {
    val context = LocalContext.current
    val latestOnPlayerReady by rememberUpdatedState(onPlayerReady)
    val latestVideoFile by rememberUpdatedState(videoFile)

    // The host's Play All command controls playback. Preparing a player must
    // not start it early and introduce timing differences between devices.
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = false
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    val exoPlayer = player

    DisposableEffect(player) {
        var reportedReadyFor: File? = null

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (
                    playbackState == Player.STATE_READY &&
                    reportedReadyFor != latestVideoFile
                ) {
                    reportedReadyFor = latestVideoFile
                    Log.d("VideoPlayer", "Video ready: ${latestVideoFile.name}")
                    latestOnPlayerReady(player)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(
                    "VideoPlayer",
                    "Playback error: ${error.errorCodeName}; ${error.message}",
                    error
                )
            }
        }

        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(videoFile) {
        Log.d(
            "VideoPlayer",
            "Loading ${videoFile.absolutePath}; exists=${videoFile.exists()}; " +
                "isFile=${videoFile.isFile}; size=${videoFile.length()}"
        )

        player.pause()
        player.playWhenReady = false

        if (!videoFile.exists() || !videoFile.isFile || videoFile.length() <= 0L) {
            Log.e("VideoPlayer", "Cannot play: video file is missing or empty")
            return@LaunchedEffect
        }

        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(videoFile)))
        player.prepare()
    }

    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                this.player = exoPlayer
                useController = true
            }
        },
        update = { playerView ->
            playerView.player = exoPlayer
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
    )
}
