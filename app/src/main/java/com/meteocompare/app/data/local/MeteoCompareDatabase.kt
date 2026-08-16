package com.meteocompare.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Version 5 : ajoute `forecast_evolution_samples` pour la comparaison run-to-run.
 * Une migration 4→5 explicite conserve le cache et l'historique de biais existants.
 *
 * Version 4 : ajoutait les tables `forecast_samples` et `observation_samples`
 * pour le suivi de biais par modèle météo (feature "chip de biais" sur
 * CityDetail).
 *
 * Stratégie de migration : la transition 4→5 est explicite et non destructive.
 * `fallbackToDestructiveMigration` reste uniquement comme compatibilité pour
 * d'anciennes versions sans chemin de migration connu. En cas de fallback :
 *   - Caches forecast perdus → régénérés au prochain refresh (quelques
 *     secondes).
 *   - Normales climatiques perdues → re-fetchées à la première consultation
 *     d'une ville (une requête archive de ~3650 lignes).
 *   - Historique de biais perdu → le bootstrap J+1 peut reconstruire jusqu'à
 *     3 semaines d'historique au prochain rafraîchissement si les archives du
 *     modèle sont disponibles ; sinon la profondeur se reconstitue au fil des jours.
 *
 * Précédent (v3) : ajout de `precipMeanNormal`, `windMeanNormal` sur
 * `climate_normals`.
 *
 * Les préférences et favoris restent dans DataStore. Room contient des données
 * dérivées et reconstruisibles, mais une recréation peut coûter du réseau et
 * perdre temporairement de la profondeur J+1 si certaines archives ne sont pas
 * disponibles : une migration explicite reste préférable pour toute future
 * évolution de schéma en production.
 */
@Database(
    entities = [
        ForecastCacheEntity::class,
        ClimateNormalEntity::class,
        ForecastSampleEntity::class,
        ObservationSampleEntity::class,
        ForecastEvolutionEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class MeteoCompareDatabase : RoomDatabase() {
    abstract fun forecastCacheDao(): ForecastCacheDao
    abstract fun climateNormalDao(): ClimateNormalDao
    abstract fun biasSampleDao(): BiasSampleDao
    abstract fun forecastEvolutionDao(): ForecastEvolutionDao
}
