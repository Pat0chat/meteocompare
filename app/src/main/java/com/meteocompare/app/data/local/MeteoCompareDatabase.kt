package com.meteocompare.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Version 7 : migration ECMWF IFS 25 km → IFS HRES 9 km. Les historiques
 * J+1 et snapshots ECMWF existants sont conservés sous l'identité legacy
 * `ECMWF_IFS025_LEGACY`, tandis que le cache forecast 25 km est invalidé.
 * Les tables qui historisent une prévision mémorisent désormais également la
 * clé API source exacte et sa résolution afin de rendre les futures migrations
 * de modèle explicitement traçables.
 *
 * Version 6 : remplace le prototype Previous Runs de `forecast_evolution_samples`
 * par des snapshots locaux des forecasts récupérés lors des refreshs frais. La table v5 est
 * reconstructible et volontairement recréée lors de 5→6 ; les autres données
 * Room restent intactes.
 *
 * Version 5 : ajoutait le premier cache d'évolution de prévision.
 *
 * Version 4 : ajoutait les tables `forecast_samples` et `observation_samples`
 * pour le suivi de biais par modèle météo (feature "chip de biais" sur
 * CityDetail).
 *
 * Stratégie de migration : 4→6 (utilisateurs v1.7.x), 5→6 (prototype v1.8)
 * puis 6→7 sont explicites. La migration 5→6 ne recrée que la table
 * d’évolution, dont le contenu est reconstruisible localement ; 6→7 conserve
 * les historiques ECMWF 25 km sous une identité legacy distincte.
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
    version = 7,
    exportSchema = false
)
abstract class MeteoCompareDatabase : RoomDatabase() {
    abstract fun forecastCacheDao(): ForecastCacheDao
    abstract fun climateNormalDao(): ClimateNormalDao
    abstract fun biasSampleDao(): BiasSampleDao
    abstract fun forecastEvolutionDao(): ForecastEvolutionDao
}
