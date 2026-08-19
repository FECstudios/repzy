package com.repzy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Before/after karşılaştırma.
 *
 * İki fotoğrafı üst üste koyup üsttekini tutamağın soluna kırpıyoruz — yan yana
 * koymaktansa aynı çerçevede kaydırmak farkı çok daha okunur kılıyor.
 * Kırpma `clipToBounds` yerine sarmalayıcı kutunun genişliğiyle yapılıyor:
 * içteki görüntü tam genişlikte kalıyor, yani sürüklerken resim esnemiyor.
 */
@Composable
fun BeforeAfterSlider(
    beforeUrl: String,
    afterUrl: String,
    beforeLabel: String,
    afterLabel: String,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val fullWidth = maxWidth
        val widthPx = with(LocalDensity.current) { fullWidth.toPx() }
        var fraction by remember { mutableFloatStateOf(0.5f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(widthPx) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        fraction = (fraction + dragAmount / widthPx).coerceIn(0f, 1f)
                    }
                },
        ) {
            AsyncImage(
                model = afterUrl,
                contentDescription = afterLabel,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // Soldaki dilim: "önce".
            //
            // Kırpma layout'ta değil ÇİZİMDE yapılıyor. Daraltılmış bir kutuya
            // yerleştirmek iki fotoğrafı hizasız bırakıyordu: dar kutu hem ölçeği
            // hem de Crop'un ortalama noktasını değiştiriyor, üstteki resim
            // alttakine göre kayıyordu. Aynı boyutta çizip sadece görünür bölgeyi
            // sınırlamak iki katmanı piksel piksel üst üste oturtuyor.
            AsyncImage(
                model = beforeUrl,
                contentDescription = beforeLabel,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        clipRect(right = size.width * fraction) { this@drawWithContent.drawContent() }
                    },
            )

            SliderLabel(beforeLabel, Modifier.align(Alignment.TopStart))
            SliderLabel(afterLabel, Modifier.align(Alignment.TopEnd))

            // Tutamak: çizgi + yuvarlak. offset ile konumlanıyor, layout'u etkilemiyor.
            Box(
                modifier = Modifier
                    .offset(x = fullWidth * fraction - HANDLE_SIZE / 2)
                    .fillMaxHeight()
                    .width(HANDLE_SIZE),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface),
                )
                Box(
                    modifier = Modifier
                        .size(HANDLE_SIZE)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.CompareArrows,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SliderLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .padding(10.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

private val HANDLE_SIZE = 40.dp
