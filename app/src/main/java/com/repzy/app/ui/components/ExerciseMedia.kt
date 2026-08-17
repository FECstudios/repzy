package com.repzy.app.ui.components

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.repzy.app.R

/**
 * Egzersiz görseli. Üç durum var ve sırayla denenir:
 *
 *  1. `animationUrl` .mp4/.webm ise → sessiz, döngülü video (ExoPlayer)
 *  2. `animationUrl` animasyonlu WebP/GIF ise → Coil (gif çözücüsü kurulu)
 *  3. Hiçbiri yoksa → yer tutucu
 *
 * Yer tutucu bilerek "yakında" demiyor, ne olduğunu söylüyor: içerik hazır
 * olmadan söz vermek istemedim. `exercises.animation_url` şu an boş, yani
 * bütün hareketler 3. durumda görünüyor.
 */
@Composable
fun ExerciseMedia(
    animationUrl: String?,
    imageUrl: String?,
    modifier: Modifier = Modifier,
) {
    val url = animationUrl ?: imageUrl

    Surface(
        shape = Decor.CardShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f),
    ) {
        when {
            url == null -> MediaPlaceholder()
            url.isVideo() -> LoopingVideo(url)
            else -> AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(Decor.CardShape),
            )
        }
    }
}

@Composable
private fun MediaPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        androidx.compose.material3.Icon(
            imageVector = Icons.Default.FitnessCenter,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.exercise_media_missing),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Sessiz, döngülü, kontrolsüz video — egzersiz gösterimi bir animasyon gibi
 * davranmalı, kullanıcı oynat/durdur ile uğraşmasın.
 */
@OptIn(UnstableApi::class)
@Composable
private fun LoopingVideo(url: String) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }

    // Ekrandan çıkınca bırakılmazsa oynatıcı arka planda kaynak tutmaya devam eder.
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    Box(Modifier.fillMaxSize().clip(Decor.CardShape)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun String.isVideo(): Boolean {
    val path = substringBefore('?').lowercase()
    return path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".m3u8")
}
