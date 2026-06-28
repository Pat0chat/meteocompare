# MeteoCompare

[![Android CI](https://github.com/Pat0chat/MeteoCompare/actions/workflows/android.yml/badge.svg)](https://github.com/Pat0chat/MeteoCompare/actions/workflows/android.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![F-Droid](https://img.shields.io/f-droid/v/com.meteocompare.app)](https://f-droid.org/packages/com.meteocompare.app/)
[![Liberapay patrons](https://img.shields.io/liberapay/patrons/Pat0chat.svg?logo=liberapay)](https://liberapay.com/Pat0chat)

Application Android de comparaison multi-modèles météorologiques (AROME, ARPEGE, ICON, GFS, ECMWF…) basée sur l'API [Open-Meteo](https://open-meteo.com).

<p align="left">
  <a href="https://f-droid.org/packages/com.meteocompare.app/">
    <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="60">
  </a>
  <a href="https://play.google.com/store/apps/details?id=com.meteocompare.app">
    <img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="60">
  </a>
</p>

## Stack technique

- **Kotlin 2.1** + Coroutines + Flow
- **Jetpack Compose** + Material 3
- **Hilt** pour l'injection de dépendances (via KSP)
- **Retrofit + OkHttp + Kotlinx Serialization** pour la couche réseau
- **DataStore Preferences** pour les favoris
- **Vico** pour les graphiques superposés
- Architecture **UI → ViewModel → Repository → API**

## Structure

```
app/src/main/java/com/meteocompare/app/
├── di/              ← Modules Hilt (Network, Repository, Dispatchers)
├── data/
│   ├── remote/      ← Interfaces Retrofit + DTOs
│   ├── mapper/      ← DTO ↔ domain
│   └── repository/  ← Implémentations
├── domain/
│   ├── model/       ← Modèles métier (City, ForecastSeries, WeatherModel…)
│   ├── repository/  ← Interfaces
│   └── usecase/     ← Use cases (à venir)
├── ui/              ← Écrans Compose (à venir)
└── core/            ← Utilitaires (ApiResult, DateTime…)
```

## Modèles supportés

Listés dans `WeatherModel.kt` avec leur résolution native (en km), horizon, et zone :

| Modèle             | Résolution | Couverture | Horizon |
|--------------------|------------|------------|---------|
| AROME France HD    | 1.5 km     | France     | 2 j     |
| AROME France       | 2.5 km     | France     | 2 j     |
| ARPEGE Europe      | 11 km      | Europe     | 4 j     |
| ARPEGE World       | 25 km      | Global     | 4 j     |
| ICON-EU            | 7 km       | Europe     | 5 j     |
| ICON               | 13 km      | Global     | 7 j     |
| GFS                | 13 km      | Global     | 16 j    |
| ECMWF              | 25 km      | Global     | 10 j    |

WRF n'est pas inclus dans cette V1 (il n'existe pas d'instance "WRF universelle" exposée par Open-Meteo). Voir feuille de route V2.

## Indice de confiance

`ConfidenceCalculator` agrège les prédictions multi-modèles
en un score 0-100 par variable (température, vent, pluie) pour chaque jour ET pour
chaque heure.

**Algorithme** :
- Pour les variables continues (T, vent) : moyenne et écart-type **pondérés**
  par `ModelWeightingStrategy`. L'écart-type est converti en % de confiance
  via des seuils calibrés (`tight`/`wide` par variable).
- Pour la pluie : agreement binaire d'abord (les modèles s'accordent-ils sur
  *l'occurrence* ?), puis spread sur l'intensité si oui.
- Pondération par défaut : `1/√résolution` — privilégie les modèles haute-résolution
  sans écraser ECMWF qui reste excellent malgré sa résolution.

```kotlin
val daily = calculator.dayConfidence(forecast, LocalDate.now())
val hourly: List<HourlyConfidenceBand> = calculator.hourlyTemperatureConfidence(forecast)
```

**Visualisation `HourlyConfidenceBand`** : un graphique de bande min-max autour
de la moyenne pondérée, sur 7 jours horaires. La bande s'élargit naturellement
quand les modèles haute-résolution disparaissent de l'horizon — c'est la signature
visuelle de l'incertitude.

Les seuils sont calibrés à partir d'observations empiriques sur l'Europe. À ajuster
quand on aura des données de skill verification.

## Premier lancement

1. Ouvrir le projet dans Android Studio.
2. Sync Gradle (le wrapper sera téléchargé automatiquement la première fois).
3. Lancer sur émulateur API 26+ ou device.

Aucune clé API n'est nécessaire — Open-Meteo est gratuit pour usage non commercial.

## Tests

```bash
./gradlew :app:testDebugUnitTest        # tests unitaires (mapper, calculator, repository)
./gradlew :app:connectedAndroidTest     # tests UI Compose + intégration Hilt (émulateur requis)
```

Couverture :
- **Unitaires** : `ForecastMapper`, `ForecastRepositoryImpl` (cache + réseau), `ConfidenceCalculator` (daily + hourly, weighting strategies).
- **Compose UI** : `CityCardTest`, `CityListContentTest`, `TodaySummaryCardTest` — tests isolés sans Hilt.
- **Intégration Hilt** : `MainActivityNavigationTest` — lance la vraie app, vérifie la DI et la navigation. Démontre le pattern `@UninstallModules` + `@TestInstallIn` pour mocker certaines couches (ex: MockWebServer).

## Accessibilité

Toutes les zones interactives ont des `contentDescription` lisibles par TalkBack :
- Les **cartes de villes** annoncent un résumé fluide ("Ville Paris, Île-de-France. Température entre 22 et 24 degrés, confiance haute, 85 pourcent.")
- Les **graphiques Canvas** (lignes par modèle, bande horaire) ont des descriptions générées par `A11yFormatter` qui résument les données clés.
- Les **titres de section** sont marqués `heading()` pour permettre la navigation par titre.

Le module `ui/accessibility/A11yFormatter.kt` centralise les chaînes pour garder une terminologie cohérente.

## CI/CD

GitHub Actions configuré (`.github/workflows/android.yml`) :
- Job `build` sur chaque push et PR : lint, tests unitaires, build APK debug.
- Job `instrumented-tests` sur PR et `main` : tests d'instrumentation sur émulateur API 33.
- Artifacts uploadés : APK debug (14 jours), rapports de tests (7 jours).

Le cache Gradle est en lecture seule pour les branches non-`main` afin d'éviter la pollution croisée.

## Politique de confidentialité

Le fichier [PRIVACY.md](PRIVACY.md) à la racine est conforme aux exigences Play Store :
zéro collecte de données, déclaration explicite des permissions, des services
tiers (Open-Meteo) et du stockage local.

À héberger sur GitHub Pages ou un Gist public, puis fournir l'URL dans Play
Console.

## Soutenir le développement

L'app est gratuite et open-source. Plusieurs options pour soutenir :

- [Liberapay](https://liberapay.com/Pat0chat) (contributions hebdomadaires)
- [GitHub Sponsors](https://github.com/sponsors/Pat0chat) (contributions mensuelles)
- [Ko-Fi](https://ko-fi.com/pat0chat) (contributions ponctuelles)

Aucun privilège n'est accordé aux donateurs — l'app et le code source
restent identiques pour tous. Voir [DONATIONS.md](DONATIONS.md) pour plus
de détails.

## Roadmap

Améliorations à venir :
- Amélioration de l'interface (en fonction des retours) ;
- Ajout de nouvelles données ;
- Ajout de nouveaux modèles ;
- Ajout de widgets ;
- ...

## Licence

[Apache License 2.0](LICENSE) — vous pouvez utiliser, modifier et
redistribuer le code librement, à condition de conserver la notice de
copyright.

Les données météo sont fournies par [Open-Meteo](https://open-meteo.com)
(également open-source, AGPL-3.0). Les modèles eux-mêmes sont produits
par leurs organismes respectifs : Météo-France (AROME, ARPEGE), DWD
(ICON), NOAA (GFS), ECMWF.
