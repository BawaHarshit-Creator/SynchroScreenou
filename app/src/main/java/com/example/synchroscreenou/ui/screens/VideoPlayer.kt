package com.example.synchroscreenou.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
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
        ExoPlayer.Builder(context).build()
    }

    LaunchedEffect(videoFile) {
        val mediaItem = MediaItem.fromUri(
            Uri.fromFile(videoFile)
        )

        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()

        onPlayerReady(player)
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
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