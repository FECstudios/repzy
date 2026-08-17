package com.repzy.app.ui.paywall

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repzy.app.R
import com.repzy.app.billing.PlanOffer
import com.repzy.app.billing.Products
import com.repzy.app.ui.components.Badge
import com.repzy.app.ui.components.Decor
import com.repzy.app.ui.components.GradientPanel

/**
 * Paywall. Onboarding sonrası bir kez ve Ayarlar'dan açılıyor.
 *
 * Tasarım kararları:
 *  - Kapatma çarpı SOL ÜSTTE ve görünür: gizli kapatma düğmesi Play politikasına
 *    aykırı ve kullanıcıyı sinirlendiriyor.
 *  - Yıllık plan öne çıkarılıyor, aylığa göre tasarruf yüzdesi hesaplanıp gösteriliyor.
 *  - Fiyat metni Play'den geldiği gibi basılıyor; para birimi/biçim bize ait değil.
 */
@Composable
fun PaywallScreen(
    onClose: () -> Unit,
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Kapatma her zaman erişilebilir olsun diye içerikten önce.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 8.dp),
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close))
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GradientPanel {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = stringResource(R.string.paywall_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.paywall_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                    )
                }
            }

            AnimatedVisibility(visible = state.isPremium) {
                Surface(
                    shape = Decor.CardShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.paywall_already_premium),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            // Karşılaştırma: ücretsiz katmanda ne var, premium'da ne değişiyor.
            BenefitRow(
                icon = Icons.Default.CameraAlt,
                title = stringResource(R.string.paywall_benefit_scans),
                subtitle = stringResource(R.string.paywall_benefit_scans_sub),
            )
            BenefitRow(
                icon = Icons.Default.AutoAwesome,
                title = stringResource(R.string.paywall_benefit_coach),
                subtitle = stringResource(R.string.paywall_benefit_coach_sub),
            )
            BenefitRow(
                icon = Icons.Default.Insights,
                title = stringResource(R.string.paywall_benefit_program),
                subtitle = stringResource(R.string.paywall_benefit_program_sub),
            )
            BenefitRow(
                icon = Icons.Default.FavoriteBorder,
                title = stringResource(R.string.paywall_benefit_support),
                subtitle = stringResource(R.string.paywall_benefit_support_sub),
            )

            // Kanit blogu: uydurma bir "%X daha iyi kas artisi" yerine yayinlanmis
            // arastirma, kaynagi ve "bu Repzy'nin olculmus sonucu degil" notuyla.
            // Dogrulanamayan etkinlik iddiasi hem yanlis hem Play red sebebi.
            Surface(
                shape = Decor.CardShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.paywall_evidence_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.paywall_evidence_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.paywall_evidence_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            if (state.storeUnavailable) {
                Surface(
                    shape = Decor.CardShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.paywall_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                state.ordered.forEach { offer ->
                    PlanCard(
                        offer = offer,
                        selected = state.selectedPlanId == offer.planId,
                        savingPercent = state.savingPercentFor(offer),
                        onClick = { viewModel.select(offer.planId) },
                    )
                }

                val selected = state.selected
                Button(
                    onClick = { activity?.let(viewModel::purchase) },
                    enabled = !state.isPurchasing && selected != null && activity != null,
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    if (state.isPurchasing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(
                            text = if (selected?.hasFreeTrial == true) {
                                stringResource(R.string.paywall_start_trial, selected.trialDays ?: 0)
                            } else {
                                stringResource(R.string.paywall_subscribe)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Denemede ne zaman ne olacağı net olsun — iptal beklentisini düşürür.
                selected?.takeIf { it.hasFreeTrial }?.let { offer ->
                    Text(
                        text = stringResource(
                            R.string.paywall_trial_explainer,
                            offer.trialDays ?: 0,
                            offer.formattedPrice,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            state.error?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = viewModel::restore) {
                    Text(stringResource(R.string.paywall_restore))
                }
                TextButton(onClick = onClose) {
                    Text(stringResource(R.string.paywall_continue_free))
                }
            }

            Text(
                text = stringResource(R.string.paywall_terms),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun BenefitRow(icon: ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlanCard(
    offer: PlanOffer,
    selected: Boolean,
    savingPercent: Int?,
    onClick: () -> Unit,
) {
    val highlight = offer.planId == Products.PLAN_YEARLY

    Card(
        onClick = onClick,
        shape = Decor.CardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, Decor.CardShape)
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Seçim göstergesi: radyo düğmesi yerine çember + tik, dokunma alanı büyük.
            Surface(
                shape = CircleShape,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
                modifier = Modifier.size(24.dp),
            ) {
                if (selected) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.size(14.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(planLabel(offer.planId)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (highlight) {
                        Spacer(Modifier.size(8.dp))
                        Badge(text = stringResource(R.string.paywall_popular))
                    }
                }
                if (offer.hasFreeTrial && offer.trialDays != null) {
                    Text(
                        text = stringResource(R.string.paywall_trial_days, offer.trialDays),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = offer.formattedPrice,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                savingPercent?.let {
                    Spacer(Modifier.height(4.dp))
                    Badge(
                        text = stringResource(R.string.paywall_saving, it),
                        container = MaterialTheme.colorScheme.tertiary,
                        content = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

private fun planLabel(planId: String): Int = when (planId) {
    Products.PLAN_YEARLY -> R.string.paywall_plan_yearly
    Products.PLAN_MONTHLY -> R.string.paywall_plan_monthly
    Products.PLAN_WEEKLY -> R.string.paywall_plan_weekly
    else -> R.string.paywall_plan_other
}
