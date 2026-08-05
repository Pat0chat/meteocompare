package com.meteocompare.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.meteocompare.app.R
import com.meteocompare.app.core.locale.applyPersistedLocale
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

    /**
     * Applique la locale persistée AVANT que les ressources soient résolues.
     *
     * Sans ce override, l'écran de config du widget affichait toujours en
     * langue système (souvent anglais US pour les devs), même quand l'app
     * était configurée en français. La bascule ici passe par le même
     * helper que MainActivity — voir [applyPersistedLocale] pour la
     * justification de l'approche SharedPreferences maison.
     */
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(applyPersistedLocale(newBase))
    }

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

        val providerClassName = runCatching {
            AppWidgetManager.getInstance(this)
                .getAppWidgetInfo(widgetId)
                ?.provider
                ?.className
        }.getOrNull()

        // Par défaut RESULT_CANCELED — si l'user quitte sans valider, le
        // système supprime le widget de l'écran (comportement standard).
        setResult(Activity.RESULT_CANCELED)

        setContent {
            MeteoCompareTheme {
                WidgetConfigScreen(
                    insightMode = isInsightWidgetProvider(providerClassName),
                    onSave = { cityId, opacityPct, forecastMode, bgColorArgb, textColorArgb ->
                        persistAndFinish(
                            widgetId = widgetId,
                            cityId = cityId,
                            opacityPct = opacityPct,
                            forecastMode = forecastMode,
                            bgColorArgb = bgColorArgb,
                            textColorArgb = textColorArgb
                        )
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
     * ─── Broadcast APPWIDGET_UPDATE et receiver dynamique ────────────────
     * Le broadcast doit cibler le receiver CORRECT parmi les 4 variantes
     * (Standard / Tiny / Wide / Large). On récupère le nom du provider via
     * l'AppWidgetProviderInfo du widgetId courant plutôt que de hardcoder
     * MeteoWidgetReceiver — sinon un widget de variante Large recevrait le
     * broadcast Standard qui n'en connaît rien.
     *
     * Contexte utilisé : `applicationContext` plutôt que `this@ConfigActivity`
     * — les opérations DataStore et le broadcast doivent survivre à finish()
     * qui annule le CoroutineScope de l'activité. `applicationContext` reste
     * valide pour toute la durée du process.
     */
    private fun persistAndFinish(
        widgetId: Int,
        cityId: String,
        opacityPct: Int,
        forecastMode: ForecastMode,
        bgColorArgb: Int?,
        textColorArgb: Int?
    ) {
        val appCtx = applicationContext
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(appCtx).getGlanceIdBy(widgetId)

            // 1. Écriture des prefs — atomique via DataStore.
            //
            // Pour les couleurs custom : si l'utilisateur a choisi "Auto"
            // (bgColorArgb / textColorArgb == null), on SUPPRIME la clé du
            // DataStore au lieu d'écrire une sentinelle. Ça garantit que la
            // lecture réactive dans MeteoWidget.provideGlance retourne null,
            // et le widget retombe sur les couleurs Material historiques.
            // Écrire une sentinelle (0, MIN_VALUE) forcerait le rendu à
            // vérifier "cette valeur est-elle une sentinelle ?" à chaque
            // lecture, source potentielle de bugs (0 est aussi une couleur
            // ARGB valide = noir transparent).
            updateAppWidgetState(
                context = appCtx,
                definition = PreferencesGlanceStateDefinition,
                glanceId = glanceId
            ) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[WidgetPreferences.CityIdKey] = cityId
                    this[WidgetPreferences.OpacityPctKey] = opacityPct
                    this[WidgetPreferences.ForecastModeKey] = forecastMode.name

                    if (bgColorArgb != null) {
                        this[WidgetPreferences.BackgroundColorKey] = bgColorArgb
                    } else {
                        remove(WidgetPreferences.BackgroundColorKey)
                    }
                    if (textColorArgb != null) {
                        this[WidgetPreferences.TextColorKey] = textColorArgb
                    } else {
                        remove(WidgetPreferences.TextColorKey)
                    }
                }
            }

            // 2. Force le BON widget à re-render avec les nouvelles prefs.
            val awm = AppWidgetManager.getInstance(appCtx)
            val providerClassName = runCatching {
                awm.getAppWidgetInfo(widgetId)?.provider?.className
            }.getOrNull()
            glanceWidgetForProviderClassName(providerClassName).update(appCtx, glanceId)

            // 3. Broadcast APPWIDGET_UPDATE ciblé sur notre widgetId. Traité
            //    par le receiver du widget après setResult+finish. Le receiver
            //    délègue à Glance, qui appelle provideGlance() avec les prefs
            //    fraîches (déjà persistées à l'étape 1). Le pattern reactive
            //    state via currentState<Preferences>() dans MeteoWidget garantit
            //    que la nouvelle valeur de cityId sera visible à la recomposition.
            //
            //    ⚠ Le receiver cible dépend de la VARIANTE du widget (Standard,
            //    Tiny, Wide, Large). On ne peut pas hardcoder MeteoWidgetReceiver
            //    — un widget Large recevrait le broadcast mais celui-ci ne le
            //    "connaît" pas au sens système. On lit AppWidgetProviderInfo
            //    pour récupérer le nom du provider réel de CE widgetId.
            val resolvedProviderClassName = providerClassName
                ?: MeteoWidgetReceiver::class.java.name
            val refreshIntent = Intent().apply {
                setClassName(appCtx.packageName, resolvedProviderClassName)
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_IDS,
                    intArrayOf(widgetId)
                )
            }
            appCtx.sendBroadcast(refreshIntent)

            // 4. S'assurer que le worker WorkManager est bien programmé. En
            //    théorie [MeteoWidgetReceiver.onEnabled] l'a déjà fait au
            //    premier drop du widget, mais un update install peut recréer
            //    les widgets sans re-appeler onEnabled (le receiver était
            //    déjà "enabled" au sens système). L'appel est idempotent
            //    (ExistingPeriodicWorkPolicy.KEEP) : le worker existant est
            //    conservé sans annulation ni replanification.
            //
            //    Note : plus besoin de lire l'intervalle utilisateur ici. La
            //    cadence du worker est maintenant fixe (voir docblock de
            //    [WidgetRefreshScheduler]). L'intervalle utilisateur est
            //    consommé au moment du loadWidgetData comme seuil
            //    `maxCacheAgeMs`, pas comme fréquence de tick.
            WidgetRefreshScheduler.schedule(appCtx)

            // 5. Résultat final au système + fin d'activité.
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
    insightMode: Boolean,
    onSave: (cityId: String, opacityPct: Int, forecastMode: ForecastMode,
             bgColorArgb: Int?, textColorArgb: Int?) -> Unit,
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
    var forecastMode by remember {
        mutableStateOf(WidgetPreferences.DEFAULT_FORECAST_MODE)
    }
    // Couleurs custom : null = Auto (Material colors via thème système).
    // C'est le défaut, mis en avant en 1re position de la palette.
    var bgColorArgb by remember { mutableStateOf<Int?>(null) }
    var textColorArgb by remember { mutableStateOf<Int?>(null) }

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
            .testTag(TAG_WIDGET_CONFIG_ROOT)
            .background(MaterialTheme.colorScheme.background)
            // systemBarsPadding : décale le contenu SOUS la barre de statut
            // (heure, batterie, notifications) et AU-DESSUS de la barre de
            // navigation. Sans ce modifier, sur les activités qui ne
            // configurent pas WindowCompat.setDecorFitsSystemWindows, le
            // contenu passe sous les system bars — le titre "Configurer le
            // widget" chevauche les icônes système.
            .systemBarsPadding()
            .padding(16.dp)
            // verticalScroll : sur petits écrans (téléphone en portrait avec
            // clavier ouvert, ou écran compact), les 3 sections + boutons
            // peuvent dépasser la hauteur disponible. Le scroll empêche que
            // les boutons Save/Cancel deviennent inaccessibles.
            .verticalScroll(rememberScrollState())
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
                // Column (pas LazyColumn) parce que le Column parent est
                // verticalScroll — imbriquer un composable scrollable dans un
                // scrollable parent lève une exception au layout. Une liste
                // de favoris tient typiquement en < 10 items, pas besoin de
                // lazy loading.
                Column {
                    favorites.forEach { city ->
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
                modifier = Modifier.weight(1f).testTag(TAG_WIDGET_OPACITY)
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
        //
        // Le preview reflète aussi les couleurs custom choisies (voir section
        // Couleurs juste après) : quand l'utilisateur switch d'Auto à Bleu,
        // le rectangle preview change de couleur — feedback immédiat qui
        // évite de valider "à l'aveugle" sur des combos illisibles.
        Spacer(Modifier.height(8.dp))
        OpacityPreview(
            opacityPct = opacityPct.toInt(),
            bgColorArgb = bgColorArgb,
            textColorArgb = textColorArgb
        )

        Spacer(Modifier.height(24.dp))

        // ─── Section couleurs ────────────────────────────────────
        // Deux palettes indépendantes : fond et texte. Chacune commence par
        // "Auto" (null) qui = thème Material. Les autres options overrident.
        //
        // ─── Pourquoi deux palettes séparées plutôt qu'un preset combiné ? ──
        // On voulait laisser au user la liberté de mixer (fond bleu foncé +
        // texte blanc, ou fond blanc + texte noir). Un preset combiné forcerait
        // des combos prédéfinis, moins flexible. Le trade-off : plus de choix
        // à faire, mais l'option "Auto" pour le texte fait 90% du boulot
        // (calcule blanc/noir selon la luminance du fond choisi) — la plupart
        // des users ne toucheront jamais la palette texte.
        Text(
            text = stringResource(R.string.widget_config_colors_section),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = stringResource(R.string.widget_config_colors_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.widget_config_colors_bg),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        ColorPaletteRow(
            options = WidgetColorPalette.Backgrounds,
            selectedArgb = bgColorArgb,
            onSelect = { bgColorArgb = it }
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.widget_config_colors_text),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        ColorPaletteRow(
            options = WidgetColorPalette.Texts,
            selectedArgb = textColorArgb,
            onSelect = { textColorArgb = it }
        )

        Spacer(Modifier.height(24.dp))

        // ─── Section horizon / mode de prévision ─────────────────────
        // Le squelette de configuration reste identique pour tous les widgets.
        // Le widget « À retenir » utilise volontairement un horizon fixe de
        // 24 heures : afficher les modes sans effet serait trompeur. On garde
        // donc la même section, avec une ligne explicative non interactive.
        Text(
            text = stringResource(R.string.widget_config_forecast_mode),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = stringResource(
                if (insightMode) R.string.widget_config_insight_horizon_note
                else R.string.widget_config_forecast_mode_note
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            if (insightMode) {
                FixedInsightHorizonRow()
            } else {
                ForecastModeRow(
                    selected = forecastMode == ForecastMode.HOURLY,
                    tag = "$TAG_WIDGET_MODE${ForecastMode.HOURLY.name}",
                    labelRes = R.string.widget_config_forecast_mode_hourly,
                    descrRes = R.string.widget_config_forecast_mode_hourly_descr,
                    onClick = { forecastMode = ForecastMode.HOURLY }
                )
                ForecastModeRow(
                    selected = forecastMode == ForecastMode.DAILY,
                    tag = "$TAG_WIDGET_MODE${ForecastMode.DAILY.name}",
                    labelRes = R.string.widget_config_forecast_mode_daily,
                    descrRes = R.string.widget_config_forecast_mode_daily_descr,
                    onClick = { forecastMode = ForecastMode.DAILY }
                )
                ForecastModeRow(
                    selected = forecastMode == ForecastMode.MINI_FORECAST_12H,
                    tag = "$TAG_WIDGET_MODE${ForecastMode.MINI_FORECAST_12H.name}",
                    labelRes = R.string.widget_config_forecast_mode_mini_12h,
                    descrRes = R.string.widget_config_forecast_mode_mini_12h_descr,
                    onClick = { forecastMode = ForecastMode.MINI_FORECAST_12H }
                )
                ForecastModeRow(
                    selected = forecastMode == ForecastMode.HEATMAP_CHART_12H,
                    tag = "$TAG_WIDGET_MODE${ForecastMode.HEATMAP_CHART_12H.name}",
                    labelRes = R.string.widget_config_forecast_mode_heatmap_12h,
                    descrRes = R.string.widget_config_forecast_mode_heatmap_12h_descr,
                    onClick = { forecastMode = ForecastMode.HEATMAP_CHART_12H }
                )
                ConfidenceModeRow(
                    selected = forecastMode.normalized() == ForecastMode.CONFIDENCE_ALL,
                    tag = "$TAG_WIDGET_MODE${ForecastMode.CONFIDENCE_ALL.name}",
                    onClick = { forecastMode = ForecastMode.CONFIDENCE_ALL }
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ─── Boutons ─────────────────────────────────────────────
        // Ancré en fin de Column plutôt qu'en bas d'écran (weight ne marche
        // pas avec verticalScroll). Sur écran compact l'utilisateur scroll
        // pour atteindre les boutons — comportement standard des formulaires
        // longs, préférable à des boutons potentiellement cachés.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onCancel, modifier = Modifier.testTag(TAG_WIDGET_CANCEL)) {
                Text(stringResource(R.string.action_cancel))
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    selectedCityId?.let { id ->
                        onSave(id, opacityPct.toInt(), forecastMode, bgColorArgb, textColorArgb)
                    }
                },
                enabled = selectedCityId != null,
                modifier = Modifier.testTag(TAG_WIDGET_SAVE)
            ) {
                Text(stringResource(R.string.widget_config_save))
            }
        }
    }
}

@Composable
internal fun FixedInsightHorizonRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_WIDGET_INSIGHT_HORIZON)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = true,
            enabled = false,
            onClick = null
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.widget_config_insight_horizon),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(R.string.widget_config_insight_horizon_descr),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ForecastModeRow(
    selected: Boolean,
    tag: String,
    labelRes: Int,
    descrRes: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            )
            .testTag(tag)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(descrRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


/**
 * Choix unique pour les deux bandes de confiance. Les chips rendent
 * explicite que température et pluie seront visibles simultanément,
 * contrairement à l'ancienne liste de trois options mutuellement exclusives.
 */
@Composable
private fun ConfidenceModeRow(
    selected: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            )
            .testTag(tag)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.widget_config_forecast_mode_conf_all),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(R.string.widget_config_forecast_mode_conf_all_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CityRow(city: City, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            )
            .testTag("$TAG_WIDGET_CITY${city.id}")
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
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
 * Rectangle qui montre visuellement l'opacité choisie ET les couleurs custom
 * si l'utilisateur en a choisi. Même couleur de fond que le widget final
 * (primaryContainer par défaut, ou custom si choisi), même arrondi.
 * L'utilisateur voit INSTANTANÉMENT à quoi ressemblera son widget sur son
 * wallpaper.
 *
 * ─── Contraste automatique ────────────────────────────────────────────
 * Quand l'utilisateur choisit un fond custom mais laisse le texte en Auto,
 * on reproduit ici la même logique que dans [MeteoWidget.WidgetContent] :
 * calcul de luminance perceptuelle du fond, blanc si sombre, noir si clair.
 * Doit rester SYNCHRONE avec le rendu widget — sinon le preview trompe.
 */
@Composable
private fun OpacityPreview(
    opacityPct: Int,
    bgColorArgb: Int?,
    textColorArgb: Int?
) {
    val alpha = opacityPct / 100f
    // Résolution du fond : custom ou primaryContainer du thème.
    val baseBg = bgColorArgb?.let { Color(it) }
        ?: MaterialTheme.colorScheme.primaryContainer
    // Résolution du texte : custom > contraste auto > onPrimaryContainer du thème.
    val fg = when {
        textColorArgb != null -> Color(textColorArgb)
        bgColorArgb != null -> {
            if (baseBg.luminance() > 0.5f)
                Color.Black
            else Color.White
        }
        alpha < 0.15f -> MaterialTheme.colorScheme.onBackground
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(baseBg.copy(alpha = alpha)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.widget_config_opacity_preview),
            color = fg,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 13.sp
        )
    }
}

/**
 * Rangée horizontale de chips colorées représentant les options de palette.
 *
 * ─── Décisions de rendu ───────────────────────────────────────────────
 * - Row horizontale + horizontalScroll : sur un téléphone portrait, 9 chips
 *   à côté ne rentrent pas. Scroll horizontal libère la contrainte de
 *   largeur.
 * - Taille des chips : 40dp × 40dp — assez grand pour un tap confortable
 *   (Material recommande ≥ 48dp pour les vraies zones de tap ; 40 marche
 *   ici parce qu'on rembourre avec spacedBy(8dp) qui étend implicitement
 *   la zone touchable au vide entre les chips grâce à l'onClick sur le Box).
 * - Chip "Auto" (argb == null) : dessinée avec un motif diagonal (Box
 *   à deux couches, TopStart/BottomEnd rotates 45°). Version simplifiée
 *   ici — juste un point d'interrogation stylisé "A" dans un cercle
 *   qui contraste avec la surface — pas besoin de reproduire un damier
 *   Photoshop-style pour signifier "aucune couleur".
 * - Sélection : anneau de 2dp autour du chip choisi, couleur primary du
 *   thème. Standard Material pour un état "selected" sur un swatch.
 */
@Composable
private fun ColorPaletteRow(
    options: List<WidgetColorOption>,
    selectedArgb: Int?,
    onSelect: (Int?) -> Unit
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        options.forEach { option ->
            ColorSwatch(
                option = option,
                selected = option.argb == selectedArgb,
                onClick = { onSelect(option.argb) }
            )
        }
    }
}

@Composable
private fun ColorSwatch(
    option: WidgetColorOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    val selectionBorder = MaterialTheme.colorScheme.primary
    val autoFill = MaterialTheme.colorScheme.surfaceContainerHigh
    val autoStroke = MaterialTheme.colorScheme.onSurfaceVariant
    val label = stringResource(option.labelRes)

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (selected) Modifier.border(
                    2.dp, selectionBorder, RoundedCornerShape(20.dp)
                ) else Modifier
            )
            .background(
                color = option.argb?.let { Color(it) }
                    ?: autoFill
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        if (option.argb == null) {
            // Chip "Auto" : simple lettre "A" en contraste avec le fond,
            // pour signaler visuellement "pas de couleur choisie, l'app
            // décide". Pas de damier ni de motif — trop chargé pour un
            // 40dp × 40dp.
            Text(
                text = "A",
                color = autoStroke,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

internal const val TAG_WIDGET_CONFIG_ROOT = "widget_config_root"
internal const val TAG_WIDGET_CITY = "widget_city_"
internal const val TAG_WIDGET_OPACITY = "widget_opacity"
internal const val TAG_WIDGET_MODE = "widget_mode_"
internal const val TAG_WIDGET_INSIGHT_HORIZON = "widget_insight_horizon"
internal const val TAG_WIDGET_SAVE = "widget_save"
internal const val TAG_WIDGET_CANCEL = "widget_cancel"
