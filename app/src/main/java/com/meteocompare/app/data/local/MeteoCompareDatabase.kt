package com.meteocompare.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Version 4 : ajoute les tables `forecast_samples` et `observation_samples`
 * pour le suivi de biais par modèle météo (feature "chip de biais" sur
 * CityDetail).
 *
 * Stratégie de migration : `fallbackToDestructiveMigration` reste actif côté
 * `DatabaseModule` — Room recrée la DB en cas d'upgrade. Impact utilisateur
 * pour cette migration précise :
 *   - Caches forecast perdus → régénérés au prochain refresh (quelques
 *     secondes).
 *   - Normales climatiques perdues → re-fetchées à la première consultation
 *     d'une ville (une requête archive de ~3650 lignes).
 *   - Historique de biais tout neuf → aucun chip n'apparaîtra tant que 14+
 *     jours ne se seront pas écoulés depuis le premier refresh. Convention
 *     produit acceptée : l'absence de chip signifie déjà "pas assez de
 *     recul".
 *
 * Précédent (v3) : ajout de `precipMeanNormal`, `windMeanNormal` sur
 * `climate_normals`.
 *
 * Les préférences et favoris restent dans DataStore. Room contient des données
 * dérivées et reconstruisibles, mais le suivi J+1 demande jusqu'à 14 jours pour
 * retrouver sa profondeur après une recréation : une migration explicite reste
 * préférable avant toute future évolution de schéma en production.
 */
@Database(
    entities = [
        ForecastCacheEntity::class,
        ClimateNormalEntity::class,
        ForecastSampleEntity::class,
        ObservationSampleEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class MeteoCompareDatabase : RoomDatabase() {
    abstract fun forecastCacheDao(): ForecastCacheDao
    abstract fun climateNormalDao(): ClimateNormalDao
    abstract fun biasSampleDao(): BiasSampleDao
}
