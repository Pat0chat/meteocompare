package com.meteocompare.app.core.locale

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import com.meteocompare.app.MainActivity
import java.util.Locale

/**
 * Enrobe [context] avec la locale persistée dans les SharedPreferences de
 * l'app. C'est la source de vérité utilisée par [MainActivity], le widget,
 * et [com.meteocompare.app.widget.MeteoWidgetConfigActivity] pour que tous
 * les composants qui affichent du texte respectent la même préférence
 * utilisateur.
 *
 * ─── Contexte : pourquoi une source de vérité maison ? ─────────────────
 * `AppCompatDelegate.getApplicationLocales()` semble être la voie officielle,
 * mais elle avait des problèmes de timing (race entre setApplicationLocales
 * et la lecture ultérieure) et exigeait AppCompatActivity comme parent — pas
 * notre cas puisqu'on est en Compose sur ComponentActivity. Voir le docblock
 * de [MainActivity.attachBaseContext] pour l'historique complet.
 *
 * Solution actuelle : les SharedPreferences maison (LOCALE_PREFS / LOCALE_KEY)
 * sont écrites synchronement par SettingsScreen et lues ici. L'intégration
 * système Android 13+ (per-app language dans Settings) reste alimentée en
 * parallèle via AppCompatDelegate, mais ce n'est plus notre source de vérité.
 *
 * ─── Points d'appel ────────────────────────────────────────────────────
 *   1. [MainActivity.attachBaseContext] — écran principal de l'app.
 *   2. `MeteoWidgetConfigActivity.attachBaseContext` — sinon la config
 *      widget affiche en anglais même si l'app est en français.
 *   3. `loadWidgetData` avant chaque `context.getString(...)` pour que les
 *      libellés du widget rendu (T°/Pluie/Vent, noms de jours, "Auj."...)
 *      suivent la préférence app, pas la locale système du device.
 *
 * ─── Effet de bord Locale.setDefault ────────────────────────────────────
 * Applique `Locale.setDefault(locale)` en même temps que la Configuration —
 * indispensable car certaines APIs (DateTimeFormatter créés via
 * `Locale.getDefault()`, NumberFormat, formatage %) lisent depuis le default
 * JVM-wide plutôt que depuis la Configuration du Context. Sans ça, les dates
 * resteraient sur la locale système même si les R.string changent.
 *
 * @return le Context d'origine si aucune préférence n'est persistée (mode
 *   "suivre la locale système"), sinon un Context enrobé.
 */
fun applyPersistedLocale(context: Context): Context {
    val tag = context
        .getSharedPreferences(MainActivity.LOCALE_PREFS, Context.MODE_PRIVATE)
        .getString(MainActivity.LOCALE_KEY, null)
    if (tag.isNullOrEmpty()) return context

    val locale = Locale.forLanguageTag(tag)
    Locale.setDefault(locale)
    val config = Configuration(context.resources.configuration)
    // minSdk 27 : LocaleList est disponible sur toutes les versions supportées.
    config.setLocales(LocaleList(locale))
    return context.createConfigurationContext(config)
}
