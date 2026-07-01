# MeteoCompare

[![Android CI](https://github.com/Pat0chat/MeteoCompare/actions/workflows/android.yml/badge.svg)](https://github.com/Pat0chat/MeteoCompare/actions/workflows/android.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![F-Droid](https://img.shields.io/f-droid/v/com.meteocompare.app)](https://f-droid.org/packages/com.meteocompare.app/)
[![Liberapay patrons](https://img.shields.io/liberapay/patrons/Pat0chat.svg?logo=liberapay)](https://liberapay.com/Pat0chat)

Application Android de comparaison multi-modèles météorologiques (AROME, ARPEGE, ICON, GFS, ECMWF, UKMO, AIFS, GEM…) basée sur l'API [Open-Meteo](https://open-meteo.com).

L'app se concentre sur **les données brutes et l'incertitude** : au lieu d'agréger silencieusement les modèles en une seule prévision, elle expose les désaccords entre modèles pour que l'utilisateur puisse juger lui-même du niveau de confiance à accorder à la prévision.

<p align="left">
  <a href="https://f-droid.org/packages/com.meteocompare.app/">
    <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="60">
  </a>
  <a href="https://play.google.com/store/apps/details?id=com.meteocompare.app">
    <img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="60">
  </a>
</p>

## Fonctionnalités

- **Comparaison multi-modèles** : jusqu'à 12 modèles météo (Météo-France, DWD, NOAA, ECMWF, UK Met Office, ECCC, plus le modèle IA d'ECMWF)
- **Indice de confiance** calculé par variable (température, vent, précipitations) et par heure
- **Page "Pourquoi cette confiance ?"** — clic sur le badge de confiance ouvre une explication détaillée : qui a prédit quoi, quel écart, pourquoi la résolution du modèle compte
- **Bande de confiance horaire** : graphique min-max autour de la moyenne pondérée qui s'élargit visuellement quand les modèles divergent
- **Tableau Jour × Modèle** des conditions météo (icônes) et températures max/min
- **Highlight du jour courant** dans tous les tableaux
- **Icônes de temps** synthétisées à partir des codes WMO 4677
- **Modes clair/sombre**, thème dynamique Material You (Android 12+)
- **Français + Anglais**
- **Aucune publicité, aucun tracker, aucune connexion sortante** hors de l'API météo

## Stack technique

- **Kotlin 2.1** + Coroutines + Flow
- **Jetpack Compose** + Material 3 (couleurs dynamiques, typographie M3, formes)
- **Hilt** pour l'injection de dépendances (via KSP)
- **Retrofit + OkHttp + Kotlinx Serialization** pour la couche réseau
- **DataStore Preferences** pour les favoris et paramètres
- **Vico** pour les graphiques superposés (températures max/min par modèle)
- Architecture **UI → ViewModel → Repository → API**, un-way data flow

## Structure

```
app/src/main/java/com/meteocompare/app/
├── di/              ← Modules Hilt (Network, Repository, Dispatchers)
├── data/
│   ├── remote/      ← Interfaces Retrofit + DTOs
│   ├── mapper/      ← DTO ↔ domain
│   ├── local/       ← Cache Room-less (JSON on-disk)
│   ├── preferences/ ← DataStore
│   └── repository/  ← Implémentations
├── domain/
│   ├── model/       ← Modèles métier (City, ForecastSeries, WeatherModel, WeatherCondition…)
│   ├── repository/  ← Interfaces
│   └── usecase/     ← ConfidenceCalculator, weighting strategies
├── ui/
│   ├── citylist/    ← Liste des villes favorites (accueil)
│   ├── citydetail/  ← Détail d'une ville : cartes, chart, tableaux
│   │   └── confidence/  ← Écran "Pourquoi cette confiance ?"
│   ├── settings/    ← Paramètres (modèles, thème, langue)
│   ├── components/  ← Composables réutilisables (WeatherIcon, ShimmerBox…)
│   ├── accessibility/ ← Formatage des descriptions TalkBack
│   ├── theme/       ← Couleurs, typographie, tokens M3
│   └── navigation/  ← Routes et NavHost
└── core/            ← Utilitaires (ApiResult, DateTime…)
```

## Modèles supportés

Listés dans `WeatherModel.kt` avec leur résolution native (km), leur horizon, leur zone de couverture, et leur institution source.

| Modèle             | Résolution | Couverture       | Horizon | Institution         | Par défaut |
|--------------------|------------|------------------|---------|---------------------|:----------:|
| AROME France HD    | 1.5 km     | France           | 2 j     | Météo-France        |     ✓      |
| AROME France       | 2.5 km     | France           | 2 j     | Météo-France        |            |
| ARPEGE Europe      | 11 km      | Europe           | 4 j     | Météo-France        |     ✓      |
| ARPEGE World       | 25 km      | Global           | 4 j     | Météo-France        |            |
| ICON-EU            | 7 km       | Europe           | 5 j     | DWD (Allemagne)     |     ✓      |
| ICON               | 13 km      | Global           | 7 j     | DWD                 |            |
| **ICON-D2**        | **2 km**   | Europe centrale  | 2 j     | DWD                 |            |
| GFS                | 13 km      | Global           | 16 j    | NOAA (USA)          |     ✓      |
| ECMWF              | 25 km      | Global           | 10 j    | ECMWF (UE)          |     ✓      |
| **ECMWF AIFS**     | **25 km**  | Global (**IA**)  | 10 j    | ECMWF               |     ✓      |
| **UKMO Global**    | **10 km**  | Global           | 7 j     | UK Met Office       |     ✓      |
| **GEM Global**     | **15 km**  | Global           | 10 j    | ECCC (Canada)       |            |

Les modèles marqués "Par défaut" sont activés dès la première ouverture ; les autres sont activables dans les Settings. **ECMWF AIFS** est notable : c'est un modèle par graph neural network (IA), pas de résolution physique — quand il s'accorde avec les modèles physiques, la confiance grimpe ; quand il diverge, c'est un signal éditorial fort exposé dans la page "Pourquoi cette confiance ?".

### Note sur AROME HD et les icônes de temps

Open-Meteo documente que "AROME France HD has the same model area, but at higher resolution with a smaller selection of weather variables" — la variable `weather_code` (code météo synthétique) n'est **pas exposée** pour AROME HD, contrairement à AROME (2.5 km) qui la fournit. Le compromis est assumé côté modèle : la résolution 1.5 km au prix d'un jeu de sorties réduit.

Pour éviter des cellules vides dans le tableau Jour × Modèle, on **infère** la condition pour AROME HD depuis les précipitations et la température min :

- Précip ≥ 5 mm → pluie (ou neige si min ≤ 0°C)
- Précip ≥ 1 mm → averses
- Précip ≥ 0.1 mm → bruine
- Précip = 0 mm → **"—" affiché** (impossible de distinguer ciel clair vs couvert sans cloud_cover, non exposé non plus par AROME HD)

Ce compromis privilégie l'**honnêteté** sur la complétude : on ne présente PAS une condition inventée sur les jours secs. Les autres modèles couvrent l'info dans ces cas.

## Indice de confiance

`ConfidenceCalculator` agrège les prédictions multi-modèles en un score 0-100 par variable pour chaque jour ET pour chaque heure.

**Algorithme** :

- Pour les variables continues (T, vent) : moyenne et écart-type **pondérés** par `ModelWeightingStrategy`. L'écart-type est converti en % de confiance via des seuils calibrés (`tight`/`wide` par variable).
- Pour la pluie : agreement binaire d'abord (les modèles s'accordent-ils sur *l'occurrence* ?), puis spread sur l'intensité si oui.
- Pour la condition météo actuelle : vote pondéré par famille (CLEAR/OVERCAST/RAIN…) — moyenner des codes WMO catégoriels n'a aucun sens. Tie-break : la condition la plus sévère.
- Pondération par défaut : `1/√résolution` — privilégie les modèles haute-résolution sans écraser ECMWF qui reste excellent malgré sa résolution.

```kotlin
val daily = calculator.dayConfidence(forecast, LocalDate.now())
val hourly: List<HourlyConfidenceBand> = calculator.hourlyTemperatureConfidence(forecast)
val currentCondition: WeatherCondition? = calculator.currentWeatherCondition(forecast)
val matrix: List<DayConditionsRow> = calculator.dailyConditionsByModel(forecast)
```

**Visualisation `HourlyConfidenceBand`** : un graphique de bande min-max autour de la moyenne pondérée, sur 7 jours horaires. La bande s'élargit naturellement quand les modèles haute-résolution disparaissent de l'horizon — c'est la signature visuelle de l'incertitude. Une timeline en dessous colore chaque tranche horaire selon le niveau de confiance (vert/orange/rouge).

Les seuils sont calibrés à partir d'observations empiriques sur l'Europe. À ajuster quand on aura des données de skill verification.

## Page "Pourquoi cette confiance ?"

Un clic sur le badge de confiance (en haut à droite de la carte "Aujourd'hui") ouvre une explication détaillée qui compose l'**edge éditorial** de l'app :

1. **Résumé du jour** avec verdict en langage naturel ("les modèles convergent fortement", "désaccord significatif"…)
2. **Une carte par variable** (température max, min, précipitations, vent) montrant :
   - Le résumé inter-modèles (valeur unique si convergence, plage si dispersion)
   - Le tableau modèle par modèle avec code couleur identique aux graphes de comparaison
   - La résolution de chaque modèle contribuant à ce jour
   - Une phrase d'interprétation qui traduit les chiffres en sens
3. **Section éducative "Pourquoi les modèles diffèrent ?"** : paragraphe pédagogique sur la résolution + tableau des modèles ayant réellement contribué + astuce AROME HD vs GFS/ECMWF

## Premier lancement

1. Ouvrir le projet dans Android Studio.
2. Sync Gradle (le wrapper sera téléchargé automatiquement la première fois).
3. Lancer sur émulateur API 26+ ou device.

Aucune clé API n'est nécessaire — Open-Meteo est gratuit pour usage non commercial.

## Tests

```bash
./gradlew :app:testDebugUnitTest        # tests unitaires (mapper, calculator, repository, WMO codes, model colors)
./gradlew :app:connectedAndroidTest     # tests UI Compose + intégration Hilt (émulateur requis)
```

Couverture :

- **Unitaires** : `ForecastMapper`, `ForecastRepositoryImpl` (cache + réseau), `ConfidenceCalculator` (daily + hourly + condition + weighting strategies), `WeatherCondition` (mapping WMO + fallback précipitation), `ModelColors` (couleurs uniques + MVP_SELECTION intégrité).
- **Compose UI** : `CityCardTest`, `CityListContentTest`, `TodaySummaryCardTest`, `ConfidenceBadgeClickTest` — tests isolés sans Hilt.
- **Intégration Hilt** : `MainActivityNavigationTest` — lance la vraie app, vérifie la DI et la navigation. Démontre le pattern `@UninstallModules` + `@TestInstallIn` pour mocker certaines couches (ex: MockWebServer).

## Accessibilité

Toutes les zones interactives ont des `contentDescription` lisibles par TalkBack :

- Les **cartes de villes** annoncent un résumé fluide qui commence par la condition actuelle : "Ville Paris, Île-de-France. Ensoleillé. Actuellement 20 degrés. Température entre 22 et 24 degrés, confiance haute, 85 pourcent."
- Le **badge de confiance** est annoncé comme bouton : "Confiance 85%, ouvrir l'explication détaillée"
- Les **graphiques Canvas** (lignes par modèle, bande horaire) ont des descriptions générées par `A11yFormatter` qui résument les données clés.
- Les **titres de section** sont marqués `heading()` pour permettre la navigation par titre.

Le module `ui/accessibility/A11yFormatter.kt` centralise les chaînes pour garder une terminologie cohérente.

## CI/CD

GitHub Actions configuré (`.github/workflows/android.yml`) :

- Job `build` sur chaque push et PR : lint, tests unitaires, build APK debug.
- Job `instrumented-tests` sur PR et `main` : tests d'instrumentation sur émulateur API 33.
- Artifacts uploadés : APK debug (14 jours), rapports de tests (7 jours).
- Symboles de débogage natifs (`native-debug-symbols.zip`) inclus dans le bundle release grâce à `ndk { debugSymbolLevel = "FULL" }` — évite le warning Play Console à chaque upload.

Le cache Gradle est en lecture seule pour les branches non-`main` afin d'éviter la pollution croisée.

## Politique de confidentialité

Le fichier [PRIVACY.md](PRIVACY.md) à la racine est conforme aux exigences Play Store : zéro collecte de données, déclaration explicite des permissions, des services tiers (Open-Meteo) et du stockage local.

À héberger sur GitHub Pages ou un Gist public, puis fournir l'URL dans Play Console.

## Soutenir le développement

L'app est gratuite et open-source. Plusieurs options pour soutenir :

- [Liberapay](https://liberapay.com/Pat0chat) (contributions hebdomadaires)
- [GitHub Sponsors](https://github.com/sponsors/Pat0chat) (contributions mensuelles)
- [Ko-Fi](https://ko-fi.com/pat0chat) (contributions ponctuelles)

Aucun privilège n'est accordé aux donateurs — l'app et le code source restent identiques pour tous. Voir [DONATIONS.md](DONATIONS.md) pour plus de détails.

## Roadmap

Fait :

- ✅ v0.0 — Comparaison multi-modèles, indice de confiance, bande horaire
- ✅ v0.1 — Page "Pourquoi cette confiance ?", correction de bugs
- ✅ v0.2 — Icônes de temps, tableau Jour × Modèle des conditions, ajout UKMO / AIFS / GEM / ICON-D2,

À venir :
- v0.3 : highlight du jour courant dans les tableaux, correction de bugs
- v0.4 : Option hourly / daily, Widget homescreen 
- v0.5 : Swipe entre villes favorites
- v0.6 : Historique de fiabilité des modèles (skill verification à partir des observations)

## Licence

[Apache License 2.0](LICENSE) — vous pouvez utiliser, modifier et redistribuer le code librement, à condition de conserver la notice de copyright.

Les données météo sont fournies par [Open-Meteo](https://open-meteo.com) (également open-source, AGPL-3.0). Les modèles eux-mêmes sont produits par leurs organismes respectifs : Météo-France (AROME, ARPEGE), DWD (ICON, ICON-D2), NOAA (GFS), ECMWF (IFS et AIFS), UK Met Office (UKMO), Environnement et Changement climatique Canada (GEM).
