package com.repzy.app.ui.photos

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.repzy.app.R
import com.repzy.app.core.Legal
import com.repzy.app.data.model.BodyPhoto
import com.repzy.app.data.model.PhotoPose
import com.repzy.app.ui.components.BeforeAfterSlider
import com.repzy.app.ui.isTurkishUi
import java.io.File

/**
 * Vücut fotoğrafları ve before/after karşılaştırma.
 *
 * Fotoğraflar AI'ya gönderilmiyor; sadece kullanıcının kendisi görüyor.
 * Ekran, ayrı fotoğraf rızası verilmeden fotoğraf çekme/seçme düğmelerini hiç göstermiyor.
 */
@Composable
fun BodyPhotosScreen(
    onBack: () -> Unit,
    viewModel: BodyPhotosViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingDelete by remember { mutableStateOf<BodyPhoto?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.addPhoto(context, uri) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = cameraUri
        if (success && uri != null) viewModel.addPhoto(context, uri)
    }

    state.comparison?.let { comparison ->
        ComparisonDialog(comparison = comparison, onClose = viewModel::closeComparison)
    }

    pendingDelete?.let { photo ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.photos_delete_title)) },
            text = { Text(stringResource(R.string.photos_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(photo)
                    pendingDelete = null
                }) {
                    Text(stringResource(R.string.photos_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
            Text(
                text = stringResource(R.string.photos_title),
                style = MaterialTheme.typography.titleLarge,
            )
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (!state.hasConsent) {
            ConsentGate(
                onGrant = viewModel::grantConsent,
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            )
            return@Column
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            state.error?.let { message ->
                ErrorRow(message = message, onDismiss = viewModel::dismissError)
                Spacer(Modifier.height(8.dp))
            }

            Text(
                text = stringResource(R.string.photos_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PhotoPose.entries.forEach { pose ->
                    FilterChip(
                        selected = state.pose == pose,
                        onClick = { viewModel.setPose(pose) },
                        label = { Text(stringResource(pose.labelRes)) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            if (state.isUploading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp))
                    Text(stringResource(R.string.photos_uploading))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val uri = createBodyPhotoUri(context)
                            cameraUri = uri
                            cameraLauncher.launch(uri)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.photos_take), maxLines = 1, softWrap = false)
                    }
                    OutlinedButton(
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.photos_pick), maxLines = 1, softWrap = false)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            val photos = state.photosOfPose
            if (photos.isEmpty()) {
                Text(
                    text = stringResource(R.string.photos_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
                return@Column
            }

            Text(
                text = stringResource(
                    if (state.canCompare) R.string.photos_compare_ready else R.string.photos_compare_hint,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            Button(
                onClick = viewModel::compareSelected,
                enabled = state.canCompare,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.photos_compare))
            }
            Spacer(Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(photos, key = { it.storagePath }) { photo ->
                    PhotoTile(
                        url = state.urls[photo.storagePath],
                        date = photo.takenOn.toString(),
                        selected = photo.storagePath in state.selected,
                        onClick = { viewModel.toggleSelection(photo) },
                        onDelete = { pendingDelete = photo },
                    )
                }
            }
        }
    }
}

/**
 * Ayrı açık rıza ekranı. Onboarding'deki sağlık verisi rızası fotoğrafı kapsamıyor:
 * KVKK spesifik rıza istiyor, "her şeye onay" geçerli sayılmıyor.
 */
@Composable
private fun ConsentGate(onGrant: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val turkish = isTurkishUi()
    var checked by remember { mutableStateOf(false) }

    Column(modifier) {
        Text(
            text = stringResource(R.string.photos_consent_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.photos_consent_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = { checked = it })
            Spacer(Modifier.size(4.dp))
            Text(
                text = stringResource(R.string.photos_consent_checkbox),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(6.dp))

        TextButton(onClick = { openUrl(context, Legal.privacyUrl(turkish)) }) {
            Text(stringResource(R.string.settings_privacy_policy))
        }
        Spacer(Modifier.height(6.dp))

        Button(onClick = onGrant, enabled = checked, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.photos_consent_accept))
        }
    }
}

@Composable
private fun PhotoTile(
    url: String?,
    date: String,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Box(
        modifier = Modifier
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (selected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
    ) {
        // URL henüz üretilmediyse boş kutu kalıyor; imzalı URL bir sonraki yüklemede geliyor.
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Text(
            text = date,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )

        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.photos_delete_title),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Karşılaştırma tam ekran açılıyor — küçük kutuda slider'ın anlamı kalmıyor. */
@Composable
private fun ComparisonDialog(comparison: Comparison, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.action_close)) }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.photos_compare),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
            }
        },
        text = {
            BeforeAfterSlider(
                beforeUrl = comparison.beforeUrl,
                afterUrl = comparison.afterUrl,
                beforeLabel = comparison.before.takenOn.toString(),
                afterLabel = comparison.after.takenOn.toString(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
            )
        },
    )
}

@Composable
private fun ErrorRow(message: String, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

private val PhotoPose.labelRes: Int
    get() = when (this) {
        PhotoPose.FRONT -> R.string.photos_pose_front
        PhotoPose.SIDE -> R.string.photos_pose_side
        PhotoPose.BACK -> R.string.photos_pose_back
    }

/** Kamera uygulamasının yazacağı geçici dosya. Cache'te, FileProvider ile paylaşılıyor. */
private fun createBodyPhotoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "body_photos").apply { mkdirs() }
    val file = File(dir, "body_${System.currentTimeMillis()}.jpg")
    return androidx.core.content.FileProvider
        .getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
}
