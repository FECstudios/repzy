package com.repzy.app.ui.nutrition

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repzy.app.R
import com.repzy.app.data.model.FoodLog
import com.repzy.app.data.model.MealItem
import com.repzy.app.data.model.MealType
import com.repzy.app.ui.components.MetricRow
import com.repzy.app.ui.components.UpgradeCard
import com.repzy.app.ui.components.MetricTile
import com.repzy.app.ui.isTurkishUi
import java.io.File
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class) // ModalBottomSheet
@Composable
fun NutritionScreen(
    onUpgradeClick: () -> Unit,
    viewModel: NutritionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val turkish = isTurkishUi()

    // Öğün saate göre önerilir; kullanıcı onay ekranında değiştirebilir.
    var meal by remember { mutableStateOf(defaultMealForNow()) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.analyzePhoto(context, uri, meal, turkish)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = cameraUri
        if (success && uri != null) viewModel.analyzePhoto(context, uri, meal, turkish)
    }

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.nutrition_title),
            style = MaterialTheme.typography.headlineMedium,
        )

        state.error?.let { message ->
            ErrorCard(message = message, onDismiss = viewModel::dismissError)
        }

        CalorieCard(state = state)

        MetricRow {
            MetricTile(
                label = stringResource(R.string.metric_protein),
                value = "${state.day.proteinG}${stringResource(R.string.unit_gram)}",
                hint = state.target?.let { "/ ${it.proteinG}${stringResource(R.string.unit_gram)}" },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            MetricTile(
                label = stringResource(R.string.metric_carbs),
                value = "${state.day.carbsG}${stringResource(R.string.unit_gram)}",
                hint = state.target?.let { "/ ${it.carbsG}${stringResource(R.string.unit_gram)}" },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            MetricTile(
                label = stringResource(R.string.metric_fat),
                value = "${state.day.fatG}${stringResource(R.string.unit_gram)}",
                hint = state.target?.let { "/ ${it.fatG}${stringResource(R.string.unit_gram)}" },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }

        if (state.isAnalyzing) {
            AnalyzingCard()
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        meal = defaultMealForNow()
                        val uri = createMealPhotoUri(context)
                        cameraUri = uri
                        cameraLauncher.launch(uri)
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.nutrition_take_photo), maxLines = 1, softWrap = false)
                }
                OutlinedButton(
                    onClick = {
                        meal = defaultMealForNow()
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.nutrition_pick_photo), maxLines = 1, softWrap = false)
                }
            }
        }

        // Tarama hakki azaldiginda upsell burada anlam kazaniyor.
        if (!state.isPremium && (state.scansRemaining ?: 99) <= 2) {
            UpgradeCard(
                onClick = onUpgradeClick,
                note = stringResource(R.string.upsell_nutrition),
            )
        }

        state.scansRemaining?.let { remaining ->
            Text(
                text = stringResource(R.string.nutrition_scans_left, remaining),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.day.logs.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.nutrition_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            MealType.entries.forEach { type ->
                val logs = state.day.byMeal(type)
                if (logs.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = mealLabel(type),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    logs.forEach { log ->
                        FoodLogRow(log = log, onDelete = { log.id?.let(viewModel::deleteLog) })
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.nutrition_estimate_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }

    state.pending?.let { pending ->
        ModalBottomSheet(onDismissRequest = viewModel::discardPending) {
            PendingSheet(
                pending = pending,
                onToggleItem = viewModel::toggleItem,
                onMealChange = viewModel::setPendingMeal,
                onConfirm = viewModel::confirmPending,
                onDiscard = viewModel::discardPending,
            )
        }
    }
}

@Composable
private fun CalorieCard(state: NutritionUiState) {
    val progress by animateFloatAsState(state.calorieProgress, label = "calorieProgress")

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.metric_calories),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (state.calorieTarget > 0) {
                    "${state.day.calories} / ${state.calorieTarget}"
                } else {
                    state.day.calories.toString()
                },
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
            )
            if (state.calorieTarget > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.nutrition_calories_left, state.caloriesLeft),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AnalyzingCard() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(12.dp))
            Text(
                text = stringResource(R.string.nutrition_analyzing),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
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

@Composable
private fun FoodLogRow(log: FoodLog, onDelete: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = log.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = macroLine(log.calories, log.proteinG, log.carbsG, log.fatG, log.grams),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_delete))
            }
        }
    }
}

@Composable
private fun PendingSheet(
    pending: PendingAnalysis,
    onToggleItem: (Int) -> Unit,
    onMealChange: (MealType) -> Unit,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
) {
    // Kalem sayisi artinca butun icerik ekrana sigmiyordu ve "Gunluge kaydet"
    // erisilemez oluyordu. Cozum: liste kaydiriliyor, butonlar altta sabit.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
        Text(
            text = stringResource(R.string.nutrition_review_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        if (pending.analysis.isLowConfidence) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.nutrition_low_confidence),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        pending.analysis.note?.let { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MealType.entries.forEach { type ->
                FilterChip(
                    selected = pending.meal == type,
                    onClick = { onMealChange(type) },
                    label = { Text(mealLabel(type)) },
                )
            }
        }

        pending.analysis.items.forEachIndexed { index, item ->
            PendingItemRow(
                item = item,
                excluded = index in pending.excluded,
                onToggle = { onToggleItem(index) },
            )
        }

        Text(
            text = stringResource(
                R.string.nutrition_review_total,
                pending.calories,
                pending.proteinG,
                pending.carbsG,
                pending.fatG,
            ),
            style = MaterialTheme.typography.titleMedium,
        )
            Spacer(Modifier.height(4.dp))
        }

        // Sabit alt blok — liste ne kadar uzun olursa olsun gorunur kalir.
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 16.dp),
            ) {
                Button(
                    onClick = onConfirm,
                    enabled = pending.canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                ) {
                    Text(stringResource(R.string.nutrition_save))
                }
                TextButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        }
    }
}

@Composable
private fun PendingItemRow(item: MealItem, excluded: Boolean, onToggle: () -> Unit) {
    Card(
        onClick = onToggle,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (excluded) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    textDecoration = if (excluded) TextDecoration.LineThrough else null,
                )
                Text(
                    text = macroLine(item.calories, item.proteinG, item.carbsG, item.fatG, item.grams),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = if (excluded) {
                    stringResource(R.string.nutrition_include)
                } else {
                    stringResource(R.string.nutrition_exclude)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun macroLine(
    calories: Double,
    protein: Double,
    carbs: Double,
    fat: Double,
    grams: Double?,
): String {
    val gram = stringResource(R.string.unit_gram)
    val portion = grams?.let { "${it.toInt()}$gram · " } ?: ""
    return "$portion${calories.toInt()} kcal · " +
        "P ${protein.toInt()}$gram · K ${carbs.toInt()}$gram · Y ${fat.toInt()}$gram"
}

@Composable
private fun mealLabel(meal: MealType): String = stringResource(
    when (meal) {
        MealType.BREAKFAST -> R.string.meal_breakfast
        MealType.LUNCH -> R.string.meal_lunch
        MealType.DINNER -> R.string.meal_dinner
        MealType.SNACK -> R.string.meal_snack
    },
)

/** Saate göre öğün tahmini — kullanıcı her seferinde seçmek zorunda kalmasın. */
private fun defaultMealForNow(): MealType {
    val hour = LocalTime.now().hour
    return when {
        hour < 11 -> MealType.BREAKFAST
        hour < 16 -> MealType.LUNCH
        hour < 22 -> MealType.DINNER
        else -> MealType.SNACK
    }
}

/** Kamera uygulamasının yazacağı geçici dosya. Cache'te, FileProvider ile paylaşılıyor. */
private fun createMealPhotoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "meal_photos").apply { mkdirs() }
    val file = File(dir, "meal_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
