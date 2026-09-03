package com.meteocompare.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.meteocompare.app.data.local.BiasSampleDao
import com.meteocompare.app.data.local.ClimateNormalDao
import com.meteocompare.app.data.local.ForecastCacheDao
import com.meteocompare.app.data.local.ForecastEvolutionDao
import com.meteocompare.app.data.local.MeteoCompareDatabase
import com.meteocompare.app.domain.model.WeatherModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MeteoCompareDatabase =
        Room.databaseBuilder(
            context,
            MeteoCompareDatabase::class.java,
            "meteocompare.db"
        )
            // Compatibilité historique : les versions publiées utilisent encore
            // une recréation destructive si aucune migration n'est fournie.
            // À ne pas reproduire lors du prochain bump : le suivi J+1 est
            // reconstructible, mais peut perdre temporairement de la profondeur si
            // les archives J+1 nécessaires au bootstrap ne sont pas disponibles.
            // dropAllTables = true : sémantique identique au comportement legacy
            // (drop-and-recreate). Room 2.7+ demande le paramètre explicite pour
            // clarifier qu'on accepte de perdre AUSSI les tables non listées
            // dans le schéma actuel (cache orphelin).
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_4_6, MIGRATION_6_7, MIGRATION_7_8)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideForecastCacheDao(database: MeteoCompareDatabase): ForecastCacheDao =
        database.forecastCacheDao()

    @Provides
    fun provideClimateNormalDao(database: MeteoCompareDatabase): ClimateNormalDao =
        database.climateNormalDao()

    @Provides
    fun provideBiasSampleDao(database: MeteoCompareDatabase): BiasSampleDao =
        database.biasSampleDao()

    @Provides
    fun provideForecastEvolutionDao(database: MeteoCompareDatabase): ForecastEvolutionDao =
        database.forecastEvolutionDao()



    /**
     * Ajoute l'échéance explicite des profils historiques. Les enregistrements
     * existants étaient exclusivement J+1 : DEFAULT 1 assure une migration
     * sans perte ni réinterprétation artificielle à J+2…J+7.
     */
    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `forecast_samples` ADD COLUMN `leadDay` INTEGER NOT NULL DEFAULT 1")
        }
    }

    /**
     * Sépare proprement l'ancien ECMWF IFS 0,25° du nouvel IFS HRES 9 km.
     *
     * - `forecast_samples` et `forecast_evolution_samples` : l'historique
     *   existant sous `ECMWF` correspond au 25 km. On le renomme en identité
     *   legacy afin que les scores/calibrations du nouvel HRES repartent de zéro.
     * - `forecast_cache` : les réponses 25 km sont invalidées. Elles sont des
     *   prévisions opérationnelles périssables et ne doivent jamais être relues
     *   comme si elles provenaient du 9 km.
     * - les trois tables reçoivent `sourceApiKey` + `resolutionKm` pour tracer
     *   explicitement la source des nouvelles données.
     */
    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `forecast_cache` ADD COLUMN `sourceApiKey` TEXT")
            db.execSQL("ALTER TABLE `forecast_cache` ADD COLUMN `resolutionKm` REAL")
            db.execSQL("UPDATE `forecast_cache` SET `sourceApiKey` = `modelKey` WHERE `sourceApiKey` IS NULL")
            db.execSQL(
                "DELETE FROM `forecast_cache` WHERE `modelKey` = '${WeatherModel.ECMWF_IFS025_API_KEY}' " +
                    "OR `sourceApiKey` = '${WeatherModel.ECMWF_IFS025_API_KEY}'"
            )

            db.execSQL("ALTER TABLE `forecast_samples` ADD COLUMN `sourceApiKey` TEXT")
            db.execSQL("ALTER TABLE `forecast_samples` ADD COLUMN `resolutionKm` REAL")
            // Défensif : une préversion ayant déjà créé la clé legacy ne doit
            // pas provoquer de collision de PK lors du renommage ci-dessous.
            db.execSQL(
                "DELETE FROM `forecast_samples` WHERE `modelKey` = '${WeatherModel.ECMWF_IFS025_LEGACY_MODEL_KEY}'"
            )
            db.execSQL(
                "UPDATE `forecast_samples` SET " +
                    "`modelKey` = '${WeatherModel.ECMWF_IFS025_LEGACY_MODEL_KEY}', " +
                    "`sourceApiKey` = '${WeatherModel.ECMWF_IFS025_API_KEY}', " +
                    "`resolutionKm` = 25.0 WHERE `modelKey` = 'ECMWF'"
            )

            db.execSQL("ALTER TABLE `forecast_evolution_samples` ADD COLUMN `sourceApiKey` TEXT")
            db.execSQL("ALTER TABLE `forecast_evolution_samples` ADD COLUMN `resolutionKm` REAL")
            db.execSQL(
                "DELETE FROM `forecast_evolution_samples` WHERE `modelKey` = '${WeatherModel.ECMWF_IFS025_LEGACY_MODEL_KEY}'"
            )
            db.execSQL(
                "UPDATE `forecast_evolution_samples` SET " +
                    "`modelKey` = '${WeatherModel.ECMWF_IFS025_LEGACY_MODEL_KEY}', " +
                    "`sourceApiKey` = '${WeatherModel.ECMWF_IFS025_API_KEY}', " +
                    "`resolutionKm` = 25.0 WHERE `modelKey` = 'ECMWF'"
            )
        }
    }

    /** Ajout du prototype v5 ; conservé pour permettre le chemin 4→5→6. */
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `forecast_evolution_samples` (
                    `cityId` TEXT NOT NULL,
                    `modelKey` TEXT NOT NULL,
                    `variable` TEXT NOT NULL,
                    `targetDateEpochDay` INTEGER NOT NULL,
                    `daysAgo` INTEGER NOT NULL,
                    `value` REAL NOT NULL,
                    `fetchedAtEpochMs` INTEGER NOT NULL,
                    PRIMARY KEY(`cityId`, `modelKey`, `variable`, `targetDateEpochDay`, `daysAgo`)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_forecast_evolution_samples_cityId_targetDateEpochDay` " +
                    "ON `forecast_evolution_samples` (`cityId`, `targetDateEpochDay`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_forecast_evolution_samples_fetchedAtEpochMs` " +
                    "ON `forecast_evolution_samples` (`fetchedAtEpochMs`)"
            )
        }
    }

    /**
     * Le contenu v5 venait de Previous Runs et n'a pas la sémantique d'un
     * historique de snapshots locaux. On recrée uniquement cette table
     * reconstructible ; caches météo, normales et historique de biais restent
     * intacts.
     */
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `forecast_evolution_samples`")
            createEvolutionSnapshotTable(db)
        }
    }



    /** Les utilisateurs de la 1.7.x (DB v4) passent d’abord au schéma v6, puis la migration 6→7 applique la séparation ECMWF HRES/legacy. */
    private val MIGRATION_4_6 = object : Migration(4, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            createEvolutionSnapshotTable(db)
        }
    }

    private fun createEvolutionSnapshotTable(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `forecast_evolution_samples` (
                `cityId` TEXT NOT NULL,
                `modelKey` TEXT NOT NULL,
                `variable` TEXT NOT NULL,
                `targetDateEpochDay` INTEGER NOT NULL,
                `snapshotBucket` INTEGER NOT NULL,
                `snapshotAtEpochMs` INTEGER NOT NULL,
                `value` REAL NOT NULL,
                PRIMARY KEY(`cityId`, `modelKey`, `variable`, `targetDateEpochDay`, `snapshotBucket`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_forecast_evolution_samples_cityId_snapshotBucket` " +
                "ON `forecast_evolution_samples` (`cityId`, `snapshotBucket`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_forecast_evolution_samples_snapshotAtEpochMs` " +
                "ON `forecast_evolution_samples` (`snapshotAtEpochMs`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_forecast_evolution_samples_cityId_targetDateEpochDay` " +
                "ON `forecast_evolution_samples` (`cityId`, `targetDateEpochDay`)"
        )
    }

}