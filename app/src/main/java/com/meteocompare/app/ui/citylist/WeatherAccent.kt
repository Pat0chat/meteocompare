package com.meteocompare.app.ui.citylist

import androidx.compose.ui.graphics.Color
import com.meteocompare.app.domain.model.WeatherCondition

/**
 * Mapping [WeatherCondition] → couleur d'accent utilisée pour le liseré supérieur
 * et les surfaces météo de chaque `CityCard` sur la liste des villes.
 *
 * ─── Rôle produit ─────────────────────────────────────────────────────────
 * Un utilisateur qui scrolle sa home doit pouvoir repérer d'un coup d'œil
 * "où il pleut / où il fait beau" sans lire les valeurs numériques. Un accent coloré discret sur la card sert de code visuel :
 *   - warm (jaune/orange) = beau
 *   - froide (bleu) = pluie
 *   - saturée (rouge) = alerte (orage)
 *   - neutre (gris) = couvert/brouillard
 *
 * ─── Choix des teintes ────────────────────────────────────────────────────
 * Palette dérivée de Material 3 mais NON tirée du colorScheme : ces couleurs
 * sont **sémantiques météo** (elles décrivent une condition atmosphérique),
 * pas des rôles UI (primary/secondary/etc.). Elles ne doivent pas changer si
 * l'utilisateur switche de thème dynamique — sinon "orage" pourrait devenir
 * bleu et briser le code visuel appris.
 *
 * Un mode dark/light minimal est prévu quand même : les teintes light sont
 * un peu plus saturées pour rester lisibles sur surface blanche ; les dark
 * sont éclaircies pour ne pas se fondre dans le fond sombre.
 *
 * ─── Ce qui N'EST PAS ici ─────────────────────────────────────────────────
 * Cette palette est utilisée pour les accents météo de la CityCard. Les
 * icônes météo (WeatherIconDecorative) ont leur propre tint sémantique
 * (`semanticTint()`), volontairement différent : l'icône traduit la nature
 * du phénomène (goutte bleue pour pluie), la barre traduit un jugement
 * "ambiance" (bleu = pas top, jaune = top). Les deux se complètent sans se
 * dupliquer.
 */
internal object WeatherAccent {

    /**
     * Retourne la couleur d'accent pour une condition donnée.
     *
     * @param condition famille météo courante. `null` → couleur neutre
     *   (typique d'un cache pré-feature sans weather_code).
     * @param isDark true si le thème courant est sombre.
     */
    fun of(condition: WeatherCondition?, isDark: Boolean): Color = when (condition) {
        null -> if (isDark) NeutralDark else NeutralLight
        WeatherCondition.CLEAR,
        WeatherCondition.MAINLY_CLEAR -> if (isDark) SunnyDark else SunnyLight

        WeatherCondition.PARTLY_CLOUDY -> if (isDark) PartlyCloudyDark else PartlyCloudyLight
        WeatherCondition.OVERCAST -> if (isDark) OvercastDark else OvercastLight
        WeatherCondition.FOG -> if (isDark) FogDark else FogLight

        WeatherCondition.DRIZZLE,
        WeatherCondition.RAIN,
        WeatherCondition.RAIN_SHOWERS -> if (isDark) RainDark else RainLight

        WeatherCondition.FREEZING_RAIN -> if (isDark) FreezingRainDark else FreezingRainLight

        WeatherCondition.SNOW,
        WeatherCondition.SNOW_SHOWERS -> if (isDark) SnowDark else SnowLight

        // Orage : couleur d'alerte (rouge sombre saturé) — clairement distincte
        // du soleil (orange chaud) ET de la pluie (indigo). Le rouge est le
        // seul code visuel universel pour "attention" ; l'utiliser ici plutôt
        // qu'un orange sombre garantit qu'un orage annoncé ne se confond pas
        // avec un beau temps. C'est la seule condition qui "crie" visuellement,
        // cohérent avec le fait qu'un orage annoncé impacte les plans de la
        // journée bien plus qu'une averse ordinaire.
        WeatherCondition.THUNDERSTORM -> if (isDark) ThunderstormDark else ThunderstormLight

        WeatherCondition.UNKNOWN -> if (isDark) NeutralDark else NeutralLight
    }

    // ─── Palette (privée, exposée uniquement via `of`) ─────────────────────
    // Chaque paire light/dark est ajustée pour un ratio de contraste raisonnable
    // sur le surface color Material 3 par défaut. On ne vise pas WCAG AA sur
    // un accent décoratif — il sert de signal, pas de texte — mais
    // elle doit rester perceptible sur les 2 thèmes.

    // Beau temps — amber saturé, chaud
    private val SunnyLight = Color(0xFFFFA726)     // orange 400
    private val SunnyDark = Color(0xFFFFB74D)      // orange 300 (éclairci)

    // Ciel partiellement couvert — beige doré, entre soleil et gris
    private val PartlyCloudyLight = Color(0xFFBCAAA4)  // brown 200
    private val PartlyCloudyDark = Color(0xFFA1887F)   // brown 300

    // Couvert — gris-bleu froid
    private val OvercastLight = Color(0xFF78909C)  // blueGrey 400
    private val OvercastDark = Color(0xFF90A4AE)   // blueGrey 300

    // Brouillard — gris neutre, presque incolore
    private val FogLight = Color(0xFF9E9E9E)       // grey 500
    private val FogDark = Color(0xFFBDBDBD)        // grey 400

    // Pluie — indigo profond
    private val RainLight = Color(0xFF3F51B5)      // indigo 500
    private val RainDark = Color(0xFF5C6BC0)       // indigo 400

    // Pluie verglaçante — cyan glacé (plus froid que la pluie, moins bleu que la neige)
    private val FreezingRainLight = Color(0xFF00838F)  // cyan 800
    private val FreezingRainDark = Color(0xFF00ACC1)   // cyan 600

    // Neige — bleu très pâle, glacé
    private val SnowLight = Color(0xFF81D4FA)      // lightBlue 200
    private val SnowDark = Color(0xFFB3E5FC)       // lightBlue 100

    // Orage — rouge sombre saturé, alerte
    // On QUITTE la famille orange (utilisée par SunnyLight/Dark) pour le rouge
    // pur : Δ RGB > 0.6 vs sunny garanti, code alerte universel. Un test unit
    // vérifie ce contraste — voir WeatherAccentTest.
    private val ThunderstormLight = Color(0xFFB71C1C)  // red 900
    private val ThunderstormDark = Color(0xFFF44336)   // red 500 (éclairci pour dark)

    // Neutre (fallback / unknown)
    private val NeutralLight = Color(0xFFBDBDBD)   // grey 400
    private val NeutralDark = Color(0xFF757575)    // grey 600
}