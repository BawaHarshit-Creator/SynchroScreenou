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

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = false
        }
    }

    DisposableEffect(player) {

        var readyReported = false

        val listener = object : Player.Listener {

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && !readyReported) {
                    readyReported = true
                    Log.d("VideoPlayer", "Video is ready for playback")
                    onPlayerReady(player)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(
                    "VideoPlayer",
                    "Playback error: ${error.errorCodeName}, message: ${error.message}",
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
            "Loading video: ${videoFile.absolutePath}, exists: ${videoFile.exists()}, size: ${videoFile.length()}"
        )

        if (videoFile.exists() && videoFile.length() > 0L) {
            val mediaItem = MediaItem.fromUri(Uri.fromFile(videoFile))
            player.setMediaItem(mediaItem)
            player.prepare()
        } else {
            Log.e("VideoPlayer", "Video file does not exist or is empty")
        }
    }

    AndroidView(
        factory = {
            PlayerView(it).apply {
                this.player = player
                useController = true
            }
        },
        update = {
            it.player = player
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
    )
}