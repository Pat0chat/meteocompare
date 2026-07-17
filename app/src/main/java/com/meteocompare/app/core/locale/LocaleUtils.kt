package com.meteocompare.app.core.locale

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import com.meteocompare.app.MainActivity
import java.util.Locale

/**
 * Cache process-wide du tag de langue persisté.
 *
 * La lecture initiale est déclenchée par
 * [com.meteocompare.app.MeteoCompareApplication.onCreate] avant l'activation
 * de StrictMode. Les Activity et widgets peuvent ensuite appliquer la locale
 * sans relire SharedPreferences sur le thread principal.
 */
private object PersistedLocaleCache {
    @Volatile
    private var initialized = false

    @Volatile
    private var languageTag: String? = null

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            languageTag = readPersistedTag(context)
            initialized = true
        }
    }

    fun refresh(context: Context) {
        val persistedTag = readPersistedTag(context)
        synchronized(this) {
            languageTag = persistedTag
            initialized = true
        }
    }

    fun update(tag: String?) {
        synchronized(this) {
            languageTag = normalizeTag(tag)
            initialized = true
        }
    }

    fun get(context: Context): String? {
        initialize(context)
        return languageTag
    }

    private fun readPersistedTag(context: Context): String? = normalizeTag(
        context.applicationContext
            .getSharedPreferences(MainActivity.LOCALE_PREFS, Context.MODE_PRIVATE)
            .getString(MainActivity.LOCALE_KEY, null)
    )

    private fun normalizeTag(tag: String?): String? = tag?.takeIf { it.isNotBlank() }
}

/**
 * Charge une fois la préférence de langue en mémoire.
 *
 * À appeler avant d'activer StrictMode afin que les composants UI ne fassent
 * pas de lecture disque lors de leur `attachBaseContext` ou de leur rendu.
 */
fun initializePersistedLocaleCache(context: Context) {
    PersistedLocaleCache.initialize(context)
}

/**
 * Persiste un nouveau choix de langue puis met immédiatement le cache mémoire
 * à jour. Cette fonction effectue une écriture disque et doit donc être appelée
 * depuis un dispatcher d'I/O.
 *
 * @return `true` si la préférence a été écrite avec succès.
 */
fun persistLocalePreference(context: Context, languageTag: String?): Boolean {
    val normalizedTag = languageTag?.takeIf { it.isNotBlank() }
    val committed = context.applicationContext
        .getSharedPreferences(MainActivity.LOCALE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(MainActivity.LOCALE_KEY, normalizedTag)
        .commit()

    if (committed) PersistedLocaleCache.update(normalizedTag)
    return committed
}

/**
 * Force une relecture disque du cache. Réservé aux tests et aux éventuelles
 * migrations de préférences ; le flux de production normal n'en a pas besoin.
 */
internal fun refreshPersistedLocaleCache(context: Context) {
    PersistedLocaleCache.refresh(context)
}

/**
 * Enrobe [context] avec la locale persistée dans les SharedPreferences de
 * l'app. C'est la source de vérité utilisée par [MainActivity], le widget,
 * et [com.meteocompare.app.widget.MeteoWidgetConfigActivity] pour que tous
 * les composants qui affichent du texte respectent la même préférence
 * utilisateur.
 *
 * La préférence n'est pas relue ici : elle provient du cache initialisé par
 * l'Application, puis maintenu à jour lors des changements effectués dans les
 * réglages. Un fallback d'initialisation existe uniquement pour les contextes
 * de tests ou les intégrations atypiques qui appelleraient cette fonction sans
 * avoir démarré l'Application.
 *
 * `Locale.setDefault(locale)` est appliqué en même temps que la Configuration,
 * car certaines APIs de formatage lisent la locale JVM par défaut plutôt que
 * celle du Context.
 *
 * @return le Context d'origine si aucune préférence n'est persistée (mode
 *   "suivre la locale système"), sinon un Context enrobé.
 */
fun applyPersistedLocale(context: Context): Context {
    val tag = PersistedLocaleCache.get(context)
    if (tag.isNullOrEmpty()) return context

    val locale = Locale.forLanguageTag(tag)
    Locale.setDefault(locale)
    val config = Configuration(context.resources.configuration)
    // minSdk 27 : LocaleList est disponible sur toutes les versions supportées.
    config.setLocales(LocaleList(locale))
    return context.createConfigurationContext(config)
}
