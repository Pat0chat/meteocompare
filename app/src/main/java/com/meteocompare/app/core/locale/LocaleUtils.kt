package com.meteocompare.app.core.locale

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.LocaleList
import com.meteocompare.app.domain.model.LanguagePreference
import java.util.Locale

/**
 * Stockage canonique de la langue de l'application.
 *
 * La locale est volontairement conservée dans un petit SharedPreferences
 * dédié plutôt que dupliquée entre DataStore, AppCompat et un cache maison.
 * Cela permet à Activity.attachBaseContext() et aux widgets de connaître la
 * langue de façon synchrone avant la résolution des ressources, tout en ayant
 * une seule source persistée et facilement sauvegardable/restaurable.
 */
internal const val LOCALE_PREFERENCES_NAME = "meteocompare_locale_prefs"
internal const val LOCALE_LANGUAGE_TAG_KEY = "language_tag"

internal fun localePreferences(context: Context): SharedPreferences =
    context.applicationContext.getSharedPreferences(
        LOCALE_PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

/** Cache process-wide du tag de langue canonique. */
private object PersistedLocaleCache {
    @Volatile
    private var initialized = false

    @Volatile
    private var languageTag: String? = null

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            languageTag = readPersistedLocaleTag(context)
            initialized = true
        }
    }

    fun refresh(context: Context) {
        val persistedTag = readPersistedLocaleTag(context)
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
}

private fun normalizeTag(tag: String?): String? = tag?.takeIf { it.isNotBlank() }

internal fun readPersistedLocaleTag(context: Context): String? = normalizeTag(
    localePreferences(context).getString(LOCALE_LANGUAGE_TAG_KEY, null)
)

internal fun currentPersistedLanguagePreference(context: Context): LanguagePreference =
    LanguagePreference.fromLanguageTag(PersistedLocaleCache.get(context))

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
 * Persiste le choix de langue canonique puis met immédiatement le cache mémoire
 * à jour. L'écriture est synchrone pour garantir qu'un `Activity.recreate()`
 * lancé juste après relise déjà la nouvelle valeur ; l'appelant doit donc
 * exécuter cette fonction hors du thread UI.
 *
 * @return `true` si la préférence a été écrite avec succès.
 */
fun persistLocalePreference(context: Context, languageTag: String?): Boolean {
    val normalizedTag = normalizeTag(languageTag)
    val editor = localePreferences(context).edit()
    if (normalizedTag == null) {
        editor.remove(LOCALE_LANGUAGE_TAG_KEY)
    } else {
        editor.putString(LOCALE_LANGUAGE_TAG_KEY, normalizedTag)
    }
    val committed = editor.commit()

    if (committed) PersistedLocaleCache.update(normalizedTag)
    return committed
}

/** Force une relecture disque du cache. Réservé aux tests/migrations. */
internal fun refreshPersistedLocaleCache(context: Context) {
    PersistedLocaleCache.refresh(context)
}

/**
 * Enrobe [context] avec la locale persistée canonique.
 *
 * Quand aucune langue n'est forcée, on remet aussi explicitement la locale JVM
 * par défaut sur celle du Context système. Cela corrige le cas `FR/EN → Système`
 * dans le même process : un ancien `Locale.setDefault(fr/en)` ne peut plus
 * continuer à influencer les formatters après le retour au mode système.
 */
fun applyPersistedLocale(context: Context): Context {
    val tag = PersistedLocaleCache.get(context)
    if (tag.isNullOrEmpty()) {
        val systemLocales = context.resources.configuration.locales
        if (!systemLocales.isEmpty) Locale.setDefault(systemLocales[0])
        return context
    }

    val locale = Locale.forLanguageTag(tag)
    Locale.setDefault(locale)
    val config = Configuration(context.resources.configuration)
    // minSdk 27 : LocaleList est disponible sur toutes les versions supportées.
    config.setLocales(LocaleList(locale))
    return context.createConfigurationContext(config)
}
