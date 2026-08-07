package com.meteocompare.app.domain.util

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/**
 * Résultat d'un calcul de sunrise/sunset pour une position et une date.
 *
 * `null` sur l'un ou l'autre = phénomène dégénéré : nuit polaire (soleil
 * jamais au-dessus de l'horizon) ou soleil de minuit (jamais sous l'horizon).
 * Ces cas arrivent aux latitudes > 66° et doivent être affichés proprement
 * plutôt que de crasher ou renvoyer 00:00.
 */
data class SunTimes(
    val sunrise: LocalTime?,
    val sunset: LocalTime?
)

/**
 * Calcul local de secours de sunrise/sunset via la formule NOAA solar calculator
 * (https://gml.noaa.gov/grad/solcalc/calcdetails.html).
 *
 * ─── Rôle actuel ──────────────────────────────────────────────────────────
 * La source principale de l'application est désormais `sunrise,sunset`
 * renvoyé par Open-Meteo dans le même appel batched que les prévisions. Ce
 * calcul local est conservé pour les anciens caches et réponses partielles.
 * Il reste :
 *   - **Pur** : ne dépend d'aucun runtime Android → testable en JVM brut
 *   - **Instantané** : ~50 opérations flottantes, aucun I/O
 *   - **Sans permission** : la position vient de la ville favorite, déjà stockée
 *   - **Robuste offline** : marche même sans réseau
 *
 * ─── Précision du fallback ─────────────────────────────────────────────────
 * Formule "vraie" avec équation du temps, déclinaison solaire, correction de
 * réfraction atmosphérique (élévation −0.833° pour l'horizon apparent, standard
 * astronomique). Précision typique : ±1 minute pour latitudes < 60°.
 * Pas d'inputs sur l'altitude — pour un utilisateur météo, ±1 min à ±100m
 * d'altitude est négligeable.
 *
 * ─── Cas dégénérés ─────────────────────────────────────────────────────────
 * `acos(x)` requiert x ∈ [-1, 1]. Aux latitudes extrêmes en été/hiver, le
 * calcul de l'angle horaire donne x hors bornes = "le soleil ne se lève/couche
 * pas ce jour-là". On renvoie null dans ces cas plutôt que d'extrapoler
 * mathématiquement à des valeurs qui n'ont pas de sens physique.
 */
object SolarTimes {

    /**
     * Retourne les heures de lever/coucher du soleil pour une date donnée.
     *
     * @param latitude en degrés. Positif = nord, négatif = sud. Plage [-90, 90].
     * @param longitude en degrés. Positif = est, négatif = ouest. Plage [-180, 180].
     * @param date jour civil dans le fuseau [zone].
     * @param zone fuseau horaire dans lequel exprimer les heures retournées.
     *   Typiquement le fuseau de la ville (ex. `Europe/Paris`).
     * @return [SunTimes] avec sunrise/sunset en heures locales du fuseau.
     *   Un ou les deux peuvent être null en région polaire (nuit continue,
     *   soleil de minuit).
     */
    fun compute(
        latitude: Double,
        longitude: Double,
        date: LocalDate,
        zone: ZoneId
    ): SunTimes {
        require(latitude in -90.0..90.0) { "latitude hors bornes: $latitude" }
        require(longitude in -180.0..180.0) { "longitude hors bornes: $longitude" }

        // ─── 1. Jour de l'année (1-365 ou 366) ──────────────────────────
        val dayOfYear = date.dayOfYear

        // ─── 2. Fractional year γ en radians ────────────────────────────
        // γ = 2π/365 * (N − 1 + (h − 12)/24) — on omet le terme horaire
        // (h=12) pour un calcul de "jour solaire moyen" qui suffit ici.
        val gamma = 2.0 * PI / 365.0 * (dayOfYear - 1)

        // ─── 3. Équation du temps (minutes) ─────────────────────────────
        // Correction entre midi solaire et midi civil (jusqu'à ±16 min sur
        // l'année). Fourier tronqué au 2e ordre — suffisant pour ±30s de
        // précision, largement en dessous du bruit d'affichage HH:mm.
        val eqTime = 229.18 * (
            0.000075 +
                0.001868 * cos(gamma) -
                0.032077 * sin(gamma) -
                0.014615 * cos(2 * gamma) -
                0.040849 * sin(2 * gamma)
            )

        // ─── 4. Déclinaison solaire δ en radians ────────────────────────
        // Angle entre le plan équatorial et le rayon Terre-Soleil. Varie de
        // ±23.44° au cours de l'année. Fourier au 3e ordre — précision au
        // dixième de degré, négligeable devant l'ordre de minute qu'on cible.
        val decl = 0.006918 -
            0.399912 * cos(gamma) +
            0.070257 * sin(gamma) -
            0.006758 * cos(2 * gamma) +
            0.000907 * sin(2 * gamma) -
            0.002697 * cos(3 * gamma) +
            0.00148 * sin(3 * gamma)

        // ─── 5. Angle horaire du lever/coucher ──────────────────────────
        // cos(ha) = [sin(-0.833°) − sin(lat)·sin(δ)] / [cos(lat)·cos(δ)]
        // Le -0.833° = -50 arc-minutes = angle sous l'horizon quand le bord
        // supérieur du disque solaire touche l'horizon apparent (réfraction
        // atmosphérique standard + demi-diamètre solaire).
        val latRad = Math.toRadians(latitude)
        val zenithRad = Math.toRadians(90.833)
        val cosHa = (
            cos(zenithRad) - sin(latRad) * sin(decl)
            ) / (cos(latRad) * cos(decl))

        // Cas dégénérés polaires : soleil ne se lève jamais (cosHa > 1)
        // ou ne se couche jamais (cosHa < -1). On retourne null pour ne
        // pas afficher d'heures fantaisistes.
        if (cosHa > 1.0) return SunTimes(sunrise = null, sunset = null)  // nuit polaire
        if (cosHa < -1.0) return SunTimes(sunrise = null, sunset = null) // soleil de minuit

        val ha = acos(cosHa) // radians
        val haDeg = Math.toDegrees(ha)

        // ─── 6. Heure UTC (minutes depuis minuit UTC) ───────────────────
        // sunrise_UTC = 720 − 4·(lon + haDeg) − eqTime
        // sunset_UTC  = 720 − 4·(lon − haDeg) − eqTime
        // Le facteur 4 min/deg vient de 360°/24h = 15°/h = 4min/deg.
        val sunriseUtcMin = 720.0 - 4.0 * (longitude + haDeg) - eqTime
        val sunsetUtcMin = 720.0 - 4.0 * (longitude - haDeg) - eqTime

        // ─── 7. Conversion vers le fuseau demandé ───────────────────────
        return SunTimes(
            sunrise = utcMinutesToLocalTime(sunriseUtcMin, date, zone),
            sunset = utcMinutesToLocalTime(sunsetUtcMin, date, zone)
        )
    }

    /**
     * Convertit des minutes depuis minuit UTC vers une heure locale dans le
     * fuseau donné. La date sert à résoudre l'offset (heures d'été).
     *
     * On passe volontairement par un `Instant` puis un `ZonedDateTime` plutôt
     * que d'ajouter/retrancher l'offset "à la main" en minutes — ça garantit
     * que les basculements été/hiver et les fuseaux non-entiers (Inde, Népal)
     * sont gérés correctement par `java.time`.
     */
    private fun utcMinutesToLocalTime(
        minutesFromUtcMidnight: Double,
        date: LocalDate,
        zone: ZoneId
    ): LocalTime {
        // Normaliser dans [0, 1440) pour gérer les cas où le calcul déborde
        // (ex. sunrise "négatif" à cause d'un fuseau très est, ou sunset > 24h
        // à l'ouest). Le modulo 1440 renvoie l'heure du même jour local qui
        // physiquement correspond.
        val normalized = ((minutesFromUtcMidnight % 1440.0) + 1440.0) % 1440.0
        val hoursUtc = (normalized / 60.0).toInt()
        val minsUtc = (normalized % 60.0).toInt()
        val secsUtc = ((normalized * 60.0) % 60.0).toInt().coerceIn(0, 59)

        return date.atTime(hoursUtc, minsUtc, secsUtc)
            .atZone(ZoneOffset.UTC)
            .withZoneSameInstant(zone)
            .toLocalTime()
    }
}
