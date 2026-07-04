package com.meteocompare.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Activité de configuration ouverte automatiquement par le système quand
 * l'utilisateur pose le widget sur son écran d'accueil (ou choisit
 * "Reconfigurer" sur long-press, Android 12+).
 *
 * Contrat système :
 *   - L'intent contient EXTRA_APPWIDGET_ID (id du widget qu'on configure).
 *   - On DOIT setResult(RESULT_OK, intent avec APPWIDGET_ID) pour valider,
 *     ou setResult(RESULT_CANCELED) pour annuler (le système supprime alors
 *     le widget automatiquement). Le résultat par défaut d'une Activity est
 *     RESULT_CANCELED — on le met explicitement pour être clair.
 *
 * Choix UX :
 *   - Liste des villes favorites (RadioButton) : simple, familier, marche avec
 *     TalkBack. Une DropdownMenu serait plus compacte mais gênerait la
 *     découvrabilité — l'utilisateur voit toutes ses villes d'un coup.
 *   - Slider d'opacité 0-100 : granularité fine plutôt que 5 presets, l'user
 *     ajuste finement au wallpaper qu'il a. Valeur affichée en % à droite pour
 *     éviter le sentiment "combien est-ce que je viens de mettre exactement ?".
 *
 * Hilt : @AndroidEntryPoint pour l'injection du CityRepository via l'EntryPoint.
 * On ne fait pas de ViewModel : la config est un one-shot, pas de state à
 * survivre à la rotation critique. LaunchedEffect(Unit) charge les favoris.
 */
@AndroidEntryPoint
class MeteoWidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            // Cas dégénéré : l'activité a été lancée sans widget id. Rien à
            // configurer, on ferme. Sans ce garde, on saverait une prefs
            // orpheline sous un id invalide.
            finish()
            return
        }

        // Par défaut RESULT_CANCELED — si l'user quitte sans valider, le
        // système supprime le widget de l'écran (comportement standard).
        setResult(Activity.RESULT_CANCELED)

        setContent {
            MeteoCompareTheme {
                WidgetConfigScreen(
                    onSave = { cityId, opacityPct ->
                        persistAndFinish(widgetId, cityId, opacityPct)
                    },
                    onCancel = { finish() }
                )
            }
        }
    }

    /**
     * Persiste la config choisie et signale RESULT_OK au système. Étapes :
     *
     *   1. Écriture des prefs Glance pour ce widget spécifique — le rendu
     *      lira ces prefs au prochain provideGlance.
     *   2. Update explicite du widget pour qu'il se recompose immédiatement.
     *   3. **Belt-and-suspenders** : broadcast APPWIDGET_UPDATE au receiver.
     *      Sur certains appareils/launchers, [MeteoWidget.update] appelée AVANT
     *      que le système ait fini d'enregistrer le widget (registration se
     *      finalise sur RESULT_OK) ne se propage pas. Le broadcast, lui, reste
     *      en file d'attente jusqu'à ce que le receiver soit joignable, et
     *      re-déclenche `provideGlance` avec les prefs fraîches. Sans ce garde,
     *      le user voit "Configurer une ville" persister plusieurs secondes
     *      après validation — et doit parfois relancer l'app pour débloquer.
     *   4. setResult + finish pour valider auprès du système.
     *
     * Contexte utilisé : `applicationContext` plutôt que `this@ConfigActivity`
     * — les opérations DataStore et le broadcast doivent survivre à finish()
     * qui annule le CoroutineScope de l'activité. `applicationContext` reste
     * valide pour toute la durée du process.
     */
    private fun persistAndFinish(widgetId: Int, cityId: String, opacityPct: Int) {
        val appCtx = applicationContext
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(appCtx).getGlanceIdBy(widgetId)

            // 1. Écriture des prefs — atomique via DataStore.
            updateAppWidgetState(
                context = appCtx,
                definition = PreferencesGlanceStateDefinition,
                glanceId = glanceId
            ) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[WidgetPreferences.CityIdKey] = cityId
                    this[WidgetPreferences.OpacityPctKey] = opacityPct
                }
            }

            // 2. Force le widget à re-render avec les nouvelles prefs.
            //    Suspend jusqu'à ce que la composition et l'update AppWidgetManager
            //    soient soumis — mais l'update ne s'applique que si le widget
            //    est déjà enregistré côté système, ce qui n'est pas garanti tant
            //    qu'on n'a pas RESULT_OK. D'où le broadcast belt-and-suspenders
            //    à l'étape 3.
            MeteoWidget().update(appCtx, glanceId)

            // 3. Broadcast APPWIDGET_UPDATE ciblé sur notre widgetId. Traité
            //    par [MeteoWidgetReceiver] après setResult+finish. Le receiver
            //    délègue à Glance, qui appelle provideGlance() avec les prefs
            //    fraîches (déjà persistées à l'étape 1).
            val refreshIntent = Intent(appCtx, MeteoWidgetReceiver::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
            }
            appCtx.sendBroadcast(refreshIntent)

            // 4. Résultat final au système + fin d'activité.
            val resultIntent = Intent()
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  Écran de configuration (Compose)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun WidgetConfigScreen(
    onSave: (cityId: String, opacityPct: Int) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current

    // Chargement des favoris via le même EntryPoint que le widget. Un ViewModel
    // serait plus propre mais surdimensionné pour un écran one-shot sans
    // navigation ni state complexe — LaunchedEffect + mutableStateOf suffisent.
    var favorites by remember { mutableStateOf<List<City>>(emptyList()) }
    var selectedCityId by remember { mutableStateOf<String?>(null) }
    var opacityPct by remember {
        mutableFloatStateOf(WidgetPreferences.DEFAULT_OPACITY_PCT.toFloat())
    }

    LaunchedEffect(Unit) {
        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        // `first()` : on ne veut que la liste actuelle. Les favoris peuvent
        // changer en arrière-plan mais l'utilisateur est dans un écran de
        // configuration momentané — pas la peine d'observer les modifications
        // externes.
        val list = entry.cityRepository().observeFavorites().first()
        favorites = list
        // Auto-sélection de la première ville — la majorité des utilisateurs
        // n'ont qu'une ville favorite, autant leur épargner un tap.
        if (list.isNotEmpty()) {
            selectedCityId = list.first().id
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.widget_config_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.widget_config_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        // ─── Section ville ────────────────────────────────────────
        Text(
            text = stringResource(R.string.widget_config_pick_city),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(8.dp))

        if (favorites.isEmpty()) {
            // Cas où l'utilisateur pose le widget sans avoir de favoris.
            // On l'oriente vers l'app plutôt que de tenter une expérience
            // dégradée (widget vide).
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.widget_config_no_favorites),
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                LazyColumn {
                    items(favorites, key = { it.id }) { city ->
                        CityRow(
                            city = city,
                            selected = city.id == selectedCityId,
                            onClick = { selectedCityId = city.id }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ─── Section opacité ────────────────────────────────────
        Text(
            text = stringResource(R.string.widget_config_opacity),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = opacityPct,
                onValueChange = { opacityPct = it },
                valueRange = 0f..100f,
                // 20 steps = 21 valeurs discrètes (0, 5, 10, …, 100). Assez
                // fin pour ajuster précisément à un wallpaper, assez grossier
                // pour que le slider ne "trémble" pas sous le doigt.
                steps = 19,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "${opacityPct.toInt()}%",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(48.dp)
            )
        }

        // Preview visuel de l'opacité choisie — l'user voit à quoi ressemblera
        // son widget avant de valider. Sans ce feedback, "80%" reste abstrait
        // et il découvrirait le rendu final seulement après validation.
        Spacer(Modifier.height(8.dp))
        OpacityPreview(opacityPct = opacityPct.toInt())

        Spacer(Modifier.weight(1f))

        // ─── Boutons ─────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.action_cancel))
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    selectedCityId?.let { id -> onSave(id, opacityPct.toInt()) }
                },
                enabled = selectedCityId != null
            ) {
                Text(stringResource(R.string.widget_config_save))
            }
        }
    }
}

@Composable
private fun CityRow(city: City, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = city.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                text = city.country,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Rectangle qui montre visuellement l'opacité choisie — même couleur de fond
 * que le widget final (primaryContainer), même arrondi. L'utilisateur voit
 * INSTANTANÉMENT à quoi ressemblera son widget sur son wallpaper.
 */
@Composable
private fun OpacityPreview(opacityPct: Int) {
    val alpha = opacityPct / 100f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.widget_config_opacity_preview),
            color = if (alpha < 0.15f)
                MaterialTheme.colorScheme.onBackground
            else MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 13.sp
        )
    }
}
