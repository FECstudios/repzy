package com.repzy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Paylaşılan görsel süslemeler. Tek yerde durmalarının sebebi: aynı degrade ve
 * köşe yarıçapı Home, Beslenme ve Paywall'da tekrar ediyor; kopyalanınca
 * biri değişip diğerleri kalıyordu.
 */
object Decor {

    val CardShape = RoundedCornerShape(22.dp)
    val ChipShape = RoundedCornerShape(12.dp)

    /** Marka degradesi: primary → tertiary. Karanlık temada da okunur kalıyor. */
    @Composable
    fun brandBrush(): Brush = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary,
        ),
    )

    /** Kart arkasına hafif bir vurgu: düz renk yerine dikey degrade. */
    @Composable
    fun surfaceBrush(): Brush = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.surface,
        ),
    )
}

/** Degrade zeminli bölüm — paywall başlığı ve koç kartında kullanılıyor. */
@Composable
fun GradientPanel(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(brush = Decor.brandBrush(), shape = Decor.CardShape)
            .padding(20.dp),
        content = content,
    )
}

/** "En popüler" / "%40 tasarruf" gibi küçük vurgu etiketi. */
@Composable
fun Badge(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primary,
    content: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Surface(
        shape = Decor.ChipShape,
        color = container,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** İnce ayırıcı — HorizontalDivider'dan daha yumuşak, bölümler arasında. */
@Composable
fun SoftDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
    )
}
