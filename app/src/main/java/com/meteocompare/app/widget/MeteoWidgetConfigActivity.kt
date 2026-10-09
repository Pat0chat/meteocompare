package com.meteocompare.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.meteocompare.app.BuildConfig
import com.meteocompare.app.R
import com.meteocompare.app.core.locale.applyPersistedLocale
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
 * LaunchedEffect charge les favoris et les préférences du widget avant le
 * formulaire ; rememberSaveable conserve les choix en cours après rotation.
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

        // Toujours partir de RESULT_CANCELED. Une Activity exportée ne doit
        // jamais valider un widget tant que l'identité du provider n'a pas été
        // vérifiée contre notre registre interne.
        setResult(Activity.RESULT_CANCELED)

        val widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val providerClassName = resolveOwnedProviderClassName(widgetId)
        if (providerClassName == null) {
            // L'id doit appartenir à un AppWidgetProvider MeteoCompare déclaré
            // dans WidgetReceivers.All. Cela bloque les lancements externes
            // forgés avec un id arbitraire ou le provider d'une autre app.
            finish()
            return
        }

        setContent {
            MeteoCompareTheme {
                WidgetConfigScreen(
                    widgetId = widgetId,
                    insightMode = isInsightWidgetProvider(providerClassName),
                    onSave = { cityId, opacityPct, forecastMode, bgColorArgb, textColorArgb, cornerStyle ->
                        persistAndFinish(
                            widgetId = widgetId,
                            expectedProviderClassName = providerClassName,
                            cityId = cityId,
                            opacityPct = opacityPct,
                            forecastMode = forecastMode,
                            bgColorArgb = bgColorArgb,
                            textColorArgb = textColorArgb,
                            cornerStyle = cornerStyle
                        )
                    },
                    onCancel = { finish() }
                )
            }
        }
    }

    /**
     * Résout le provider réel du widget et vérifie qu'il appartient bien à
     * MeteoCompare et au registre des receivers autorisés.
     *
     * Le fallback d'instrumentation n'existe que dans les builds DEBUG afin
     * que les tests UI puissent rendre l'écran sans launcher/AppWidgetHost. Il
     * est totalement absent du chemin de décision d'une release.
     */
    private fun resolveOwnedProviderClassName(widgetId: Int): String? {
        val provider = runCatching {
            AppWidgetManager.getInstance(this)
                .getAppWidgetInfo(widgetId)
                ?.provider
        }.getOrNull()

        val realProviderClass = provider?.className?.takeIf { className ->
            isOwnedWidgetProvider(
                appPackageName = packageName,
                providerPackageName = provider.packageName,
                providerClassName = className
            )
        }
        if (realProviderClass != null) return realProviderClass

        if (!BuildConfig.DEBUG) return null
        return intent?.getStringExtra(EXTRA_DEBUG_PROVIDER_CLASS_NAME)
            ?.takeIf(::isRegisteredWidgetProviderClassName)
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
        expectedProviderClassName: String,
        cityId: String,
        opacityPct: Int,
        forecastMode: ForecastMode,
        bgColorArgb: Int?,
        textColorArgb: Int?,
        cornerStyle: WidgetCornerStyle
    ) {
        val appCtx = applicationContext
        lifecycleScope.launch {
            try {
                // Revalidation au moment du Save (TOCTOU) : l'id doit toujours
                // pointer vers le même provider MeteoCompare qu'à l'ouverture.
                val currentProviderClassName = resolveOwnedProviderClassName(widgetId)
                    ?: run {
                        finish()
                        return@launch
                    }
                if (currentProviderClassName != expectedProviderClassName) {
                    finish()
                    return@launch
                }

                val glanceId = GlanceAppWidgetManager(appCtx).getGlanceIdBy(widgetId)

                // 1. Écriture des prefs — atomique via DataStore. Pour les
                // couleurs custom, l'absence de valeur signifie "Auto".
                updateAppWidgetState(
                    context = appCtx,
                    definition = PreferencesGlanceStateDefinition,
                    glanceId = glanceId
                ) { prefs ->
                    prefs.toMutablePreferences().apply {
                        WidgetConfiguration(
                            cityId = cityId,
                            opacityPct = opacityPct,
                            forecastMode = forecastMode,
                            backgroundColorArgb = bgColorArgb,
                            textColorArgb = textColorArgb,
                            cornerStyle = cornerStyle
                        ).writeTo(this)
                    }
                }

                // 2. Force le BON widget à re-render avec les nouvelles prefs.
                glanceWidgetForProviderClassName(currentProviderClassName)
                    .update(appCtx, glanceId)

                // 3. Broadcast APPWIDGET_UPDATE ciblé sur le provider validé.
                val refreshIntent = Intent().apply {
                    setClassName(appCtx.packageName, currentProviderClassName)
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(
                        AppWidgetManager.EXTRA_APPWIDGET_IDS,
                        intArrayOf(widgetId)
                    )
                }
                appCtx.sendBroadcast(refreshIntent)

                // 4. Garantit la présence du worker de rafraîchissement.
                WidgetRefreshScheduler.schedule(appCtx)

                // 5. Résultat final au système + fin d'activité.
                val resultIntent = Intent()
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Unable to persist widget configuration", error)
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        }
    }

    companion object {
        private const val TAG = "MeteoCompare/WidgetConfig"
        internal const val EXTRA_DEBUG_PROVIDER_CLASS_NAME =
            "com.meteocompare.app.extra.DEBUG_WIDGET_PROVIDER"
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  Écran de configuration (Compose)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun WidgetConfigScreen(
    widgetId: Int,
    insightMode: Boolean,
    onSave: (cityId: String, opacityPct: Int, forecastMode: ForecastMode,
             bgColorArgb: Int?, textColorArgb: Int?, cornerStyle: WidgetCornerStyle) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    var initialData by remember(widgetId) {
        mutableStateOf<Pair<List<City>, WidgetConfiguration>?>(null)
    }
    var loadFailed by remember(widgetId) { mutableStateOf(false) }
    var loadAttempt by remember(widgetId) { mutableStateOf(0) }

    LaunchedEffect(widgetId, loadAttempt) {
        loadFailed = false
        try {
            val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(widgetId)
            val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
            val entry = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
            val favorites = entry.cityRepository().observeFavorites().first()
            initialData = favorites to WidgetConfiguration.fromPreferences(prefs)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w("MeteoCompare/WidgetConfig", "Unable to read widget configuration", error)
            loadFailed = true
        }
    }

    val loaded = initialData
    if (loaded == null) {
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (loadFailed) {
                Text(stringResource(R.string.widget_config_load_error))
                Button(onClick = { loadAttempt++ }) { Text(stringResource(R.string.action_retry)) }
            } else {
                CircularProgressIndicator()
                Text(stringResource(R.string.widget_error_loading))
            }
            TextButton(onClick = onCancel, modifier = Modifier.testTag(TAG_WIDGET_CANCEL)) {
                Text(stringResource(R.string.action_cancel))
            }
        }
        return
    }

    WidgetConfigForm(widgetId, insightMode, loaded.first, loaded.second, onSave, onCancel)
}

@Composable
private fun WidgetConfigForm(
    widgetId: Int,
    insightMode: Boolean,
    favorites: List<City>,
    initial: WidgetConfiguration,
    onSave: (cityId: String, opacityPct: Int, forecastMode: ForecastMode,
             bgColorArgb: Int?, textColorArgb: Int?, cornerStyle: WidgetCornerStyle) -> Unit,
    onCancel: () -> Unit
) {
    var selectedCityId by rememberSaveable(widgetId) {
        mutableStateOf(initial.selectableCityId(favorites))
    }
    var opacityPct by rememberSaveable(widgetId) { mutableFloatStateOf(initial.opacityPct.toFloat()) }
    var opacityInput by rememberSaveable(widgetId) { mutableStateOf(initial.opacityPct.toString()) }
    // Keep draft invalid input visible, but never save it or change the live preview.
    val opacityIsValid = opacityInput.toIntOrNull()?.let { it in 0..100 } == true
    var forecastMode by rememberSaveable(widgetId) { mutableStateOf(initial.forecastMode) }
    // null = couleurs automatiques ; 0 reste une couleur transparente explicite.
    var bgColorArgb by rememberSaveable(widgetId) { mutableStateOf(initial.backgroundColorArgb) }
    var textColorArgb by rememberSaveable(widgetId) { mutableStateOf(initial.textColorArgb) }
    var cornerStyle by rememberSaveable(widgetId) { mutableStateOf(initial.cornerStyle) }
    var editingColor by rememberSaveable(widgetId) { mutableStateOf<String?>(null) }

    if (editingColor != null) {
        WidgetColorWheelDialog(
            originalArgb = if (editingColor == "bg") bgColorArgb else textColorArgb,
            onDismiss = { editingColor = null },
            onApply = { argb ->
                if (editingColor == "bg") bgColorArgb = argb else textColorArgb = argb
                editingColor = null
            }
        )
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
                onValueChange = {
                    val percentage = it.roundToInt().coerceIn(0, 100)
                    opacityPct = percentage.toFloat()
                    opacityInput = percentage.toString()
                },
                valueRange = 0f..100f,
                // 99 intermediate values = every integer percentage from 0 to 100.
                steps = 99,
                modifier = Modifier.weight(1f).testTag(TAG_WIDGET_OPACITY)
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = opacityInput,
                onValueChange = { entered ->
                    if (entered.length <= 3 && entered.all(Char::isDigit)) {
                        opacityInput = entered
                        entered.toIntOrNull()?.takeIf { it in 0..100 }?.let {
                            opacityPct = it.toFloat()
                        }
                    }
                },
                modifier = Modifier.width(116.dp).testTag(TAG_WIDGET_OPACITY_INPUT),
                singleLine = true,
                isError = !opacityIsValid,
                suffix = { Text("%") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text(stringResource(R.string.widget_opacity_manual_label)) }
            )
        }

        // Preview visuel de l'opacité choisie — l'user voit à quoi ressemblera
        // son widget avant de valider. Sans ce feedback, un pourcentage reste abstrait
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
            textColorArgb = textColorArgb,
            cornerStyle = cornerStyle
        )

        Spacer(Modifier.height(24.dp))

        // ─── Section couleurs ────────────────────────────────────
        // Pastilles rapides + roue HSV pour chaque couleur. Les Int ARGB
        // existants permettent la reprise exacte de toute couleur libre.
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
        Spacer(Modifier.height(8.dp))
        ColorPaletteRow(
            options = WidgetColorPalette.Backgrounds,
            selectedArgb = bgColorArgb,
            onSelect = { bgColorArgb = it }
        )

        // Les palettes restent regroupées : les couleurs libres sont une
        // section À PART, et non une action collée aux dernières pastilles.
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.widget_config_colors_text),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(8.dp))
        ColorPaletteRow(
            options = WidgetColorPalette.Texts,
            selectedArgb = textColorArgb,
            onSelect = { textColorArgb = it }
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.widget_color_custom),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(14.dp))
        WidgetCustomColorButton(
            color = bgColorArgb,
            labelRes = R.string.widget_config_colors_bg,
            tag = TAG_WIDGET_CUSTOM_BG,
            onClick = { editingColor = "bg" }
        )
        Spacer(Modifier.height(12.dp))
        WidgetCustomColorButton(
            color = textColorArgb,
            labelRes = R.string.widget_config_colors_text,
            tag = TAG_WIDGET_CUSTOM_TEXT,
            onClick = { editingColor = "text" }
        )

        Spacer(Modifier.height(30.dp))

        // Seule la forme du fond EXTERNE change, pas les cartes intérieures.
        Text(
            text = stringResource(R.string.widget_config_corner_style),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            WidgetCornerOption(
                label = stringResource(R.string.widget_corner_rounded),
                selected = cornerStyle == WidgetCornerStyle.ROUNDED,
                tag = TAG_WIDGET_CORNER_ROUNDED,
                onClick = { cornerStyle = WidgetCornerStyle.ROUNDED }
            )
            WidgetCornerOption(
                label = stringResource(R.string.widget_corner_square),
                selected = cornerStyle == WidgetCornerStyle.SQUARE,
                tag = TAG_WIDGET_CORNER_SQUARE,
                onClick = { cornerStyle = WidgetCornerStyle.SQUARE }
            )
        }
        Text(
            text = stringResource(R.string.widget_corner_launcher_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                ForecastModeRow(
                    selected = forecastMode == ForecastMode.HEATMAP_TREND_12H,
                    tag = "$TAG_WIDGET_MODE${ForecastMode.HEATMAP_TREND_12H.name}",
                    labelRes = R.string.widget_config_forecast_mode_heatmap_trend_12h,
                    descrRes = R.string.widget_config_forecast_mode_heatmap_trend_12h_descr,
                    onClick = { forecastMode = ForecastMode.HEATMAP_TREND_12H }
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
                        onSave(id, opacityPct.toInt(), forecastMode, bgColorArgb, textColorArgb, cornerStyle)
                    }
                },
                enabled = selectedCityId != null && opacityIsValid,
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
 * Choix unique pour les deux bandes de convergence. Les chips rendent
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
    textColorArgb: Int?,
    cornerStyle: WidgetCornerStyle
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
    // Représenter aussi la marge TRANSPARENTE imposée en mode carré : un
    // aperçu plein-bord donnait jusqu'ici un résultat trompeur sur Pixel.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(68.dp)) {
        val (insetX, insetY) = squarePanelInsetsDp(maxWidth.value, maxHeight.value)
        Box(
            modifier = Modifier.fillMaxSize().then(
                if (cornerStyle == WidgetCornerStyle.SQUARE) {
                    Modifier.padding(horizontal = insetX.dp, vertical = insetY.dp)
                } else Modifier
            ).clip(RoundedCornerShape(if (cornerStyle == WidgetCornerStyle.SQUARE) 0.dp else 16.dp))
                .background(baseBg.copy(alpha = alpha)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.widget_config_opacity_preview),
                color = fg,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 13.sp
            )
        }
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

internal const val TAG_WIDGET_CUSTOM_BG = "widget_custom_bg"
internal const val TAG_WIDGET_CUSTOM_TEXT = "widget_custom_text"
internal const val TAG_WIDGET_CORNER_ROUNDED = "widget_corner_rounded"
internal const val TAG_WIDGET_CORNER_SQUARE = "widget_corner_square"
internal const val TAG_WIDGET_CONFIG_ROOT = "widget_config_root"
internal const val TAG_WIDGET_CITY = "widget_city_"
internal const val TAG_WIDGET_OPACITY = "widget_opacity"
internal const val TAG_WIDGET_OPACITY_INPUT = "widget_opacity_input"
internal const val TAG_WIDGET_MODE = "widget_mode_"
internal const val TAG_WIDGET_INSIGHT_HORIZON = "widget_insight_horizon"
internal const val TAG_WIDGET_SAVE = "widget_save"
internal const val TAG_WIDGET_CANCEL = "widget_cancel"
