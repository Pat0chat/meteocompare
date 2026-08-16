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
            .addMigrations(MIGRATION_4_5)
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

    /** Ajout non destructif du cache run-to-run : les données v4 restent intactes. */
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
}