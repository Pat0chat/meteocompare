# MeteoCompare

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![F-Droid](https://img.shields.io/f-droid/v/com.meteocompare.app)](https://f-droid.org/packages/com.meteocompare.app/)
[![Liberapay patrons](https://img.shields.io/liberapay/patrons/Pat0chat.svg?logo=liberapay)](https://liberapay.com/Pat0chat)

Application Android de comparaison multi-modèles météorologiques (AROME, ARPEGE, ICON, GFS, HRRR, ECMWF, UKMO, AIFS, GEM, MET Nordic, HARMONIE, ACCESS, GRAPES…) basée sur l'API [Open-Meteo](https://open-meteo.com).

L'app se concentre sur **les données brutes et l'incertitude** : au lieu d'agréger silencieusement les modèles en une seule prévision, elle expose les désaccords entre modèles pour que l'utilisateur puisse juger lui-même du niveau de confiance à accorder à la prévision.

Depuis la v1.0, l'app suit aussi **le biais historique de chaque modèle sur chaque ville favorite** — comparaison des prévisions J+1 passées à une référence de réanalyse historique Open-Meteo. Cette référence combine observations assimilées et modélisation : elle ne doit pas être confondue avec une station au point exact. Les écarts systématiques sont signalés d'une pastille discrète dans les tableaux, sans jamais modifier la donnée brute.

<p>
  <a href="https://f-droid.org/packages/com.meteocompare.app/">
    <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="60">
  </a>
  <a href="https://play.google.com/store/apps/details?id=com.meteocompare.app">
    <img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="60">
  </a>
</p>

## Fonctionnalités

- **Comparaison multi-modèles** : jusqu'à 17 modèles météo (Météo-France, DWD, NOAA, ECMWF, UK Met Office, ECCC, MET Norway, KNMI, BOM, CMA, plus le modèle IA d'ECMWF)
- **Indice d’accord inter-modèles** calculé par variable (température, vent, précipitations) et par heure ; il décrit le spread des scénarios et n’est pas une probabilité de justesse
- **Résumé « Aujourd’hui » enrichi** : quatre mini-cartes homogènes (température min/max, précipitations, vent) affichent la moyenne des modèles, la plage prévue, la dispersion utile, les rafales lorsqu’elles existent et le niveau d’accord par variable
- **Page "Pourquoi cette confiance ?"** — clic sur le badge de confiance ouvre une explication détaillée : qui a prédit quoi, quel écart, pourquoi la résolution du modèle compte
- **Suivi de biais par modèle et par ville** — chaque modèle est confronté à une réanalyse historique Open-Meteo sur ses prévisions J+1 passées. Trois pastilles : biais systématique significatif, biais signé faible, ou historique encore insuffisant. La sheet 30 jours compare prévision et référence avec moyenne, écart-type et contexte méthodologique
- **Évolution des prévisions (~24 / ~48 / ~72 h)** : chaque refresh météo frais enregistre localement un snapshot quotidien (température max, cumul de pluie, vent max), au plus une fois par tranche de 3 h et avec 5 jours de rétention. La fiche ville compare ensuite la prévision courante aux snapshots les plus proches de 24/48/72 h et affiche leur âge réel (par ex. H−25). Ce n'est pas une reconstruction des cycles 00Z/06Z/12Z/18Z d'un modèle. Les médianes gardent le même groupe de modèles comparables, les données manquantes sont exclues, les changements importants remontent dans « À retenir », et la carte repliable est mémorisée par ville. Aucun appel réseau supplémentaire n'est déclenché par cette fonctionnalité ; après installation, l'historique se construit progressivement.
- **Bande de confiance horaire multi-métriques** : sélecteur segmenté à 3 états pour basculer entre température, précipitations et vent — la bande se recalcule instantanément (précalcul dans le ViewModel). Graphique min-max autour de la moyenne pondérée qui s'élargit visuellement quand les modèles divergent
- **Repères thermiques 10 ans en overlay** : Tmax/Tmin calendaires calculées sur la réanalyse ERA5 et affichées en traits pointillés sur la bande température. Les anciennes références pluie/vent journalières ne sont plus superposées aux graphes horaires, car les fenêtres temporelles ne sont pas comparables (et il ne s’agit pas de « normales climatiques » officielles sur 30 ans).
- **Zoom au pincement** sur l'axe temps (double-tap pour réinitialiser)
- **Toggle "par heure / par jour"** : bascule les tableaux entre la vue synthétique 7 jours et le détail horaire jusqu'à la fin de la journée courante
- **Tableau Jour × Modèle** des conditions météo (icônes) et températures max/min, avec badges "%" indiquant la couverture nuageuse (cellules nuageuses/couvertes) ou la probabilité de pluie (cellules pluvieuses)
- **Direction du vent** : flèches *downwind* dans les tableaux vent quand la vitesse dépasse 5 km/h (au-dessous, la direction est du bruit)
- **Icônes de temps** synthétisées à partir des codes WMO 4677, dont un composite bi-color soleil + nuage pour "partiellement nuageux"
- **"Fraîcheur" des données** affichée sur chaque carte : "Mis à jour à l'instant", "il y a 5 min", etc. — auto-rafraîchi au fil du temps
- **Cartes Home compactes** : accent météo vertical sur le bord gauche, métriques resserrées, pastille « N scénarios » repliable et information de mise à jour réunies sur une seule ligne
- **Heatmap 12 h intégrée aux cartes Home** : 12 cellules thermiques continues avec température par heure, trois repères horaires directement dans la bande et marqueur de pluie à partir de 30 %, sans ajouter une ligne supplémentaire sous la heatmap
- **Chronologie visuelle sur la page détail** : timeline compacte des prochaines échéances avec heatmap de température, pluie, vent, accord inter-modèles et mise en évidence des changements significatifs
- **Highlight du jour courant** (et de l'heure courante en mode hourly) dans tous les tableaux
- **Widgets écran d'accueil** (Glance) redimensionnables 2×1 / 3×1 / 4×1 / 4×2, avec en 4×2 le choix entre 4 prochaines heures, 4 prochains jours, ou une mini bande de confiance (T° / pluie / vent) avec valeurs par jour
- **Tri des modèles dans les Settings** par zone / famille / finesse
- **Batching multi-modèles** : les N modèles activés sont récupérés en 1 seule requête HTTPS (au lieu de N requêtes parallèles) — gain sur la latence et la batterie
- **Modes clair/sombre**, thème dynamique Material You (Android 12+)
- **Français + Anglais + Espagnol + Allemand + Italien** (widgets inclus — le rendu suit la préférence app, pas la locale système)
- **Aucune publicité, aucun tracker, aucune connexion sortante** hors de l'API météo

## Stack technique

- **Gradle 8.14** + **Android Gradle Plugin 8.13**
- **Kotlin 2.3** + Coroutines + Flow
- **Jetpack Compose** + Material 3 (couleurs dynamiques, typographie M3, formes)
- **Hilt** pour l'injection de dépendances (via KSP)
- **Retrofit + OkHttp + Kotlinx Serialization** pour la couche réseau
- **Room** pour le cache local (forecasts, repères climatiques 10 ans)
- **DataStore Preferences** pour les favoris et paramètres
- **SharedPreferences dédié** pour la langue de l’app, utilisée comme source persistée unique par l’application et les widgets
- **Glance** pour les widgets
- Architecture **UI → ViewModel → Repository → API**, un-way data flow

## Structure

```
app/src/main/java/com/meteocompare/app/
├── di/              ← Modules Hilt (Network, Repository, Dispatchers)
├── core/
│   ├── locale/      ← LocaleUtils / applyPersistedLocale — SharedPreferences dédié, source unique app + widgets
│   └── network/     ← ApiResult, NetworkMonitor, error mapping
├── data/
│   ├── remote/      ← Interfaces Retrofit + DTOs + BatchedForecastSplitter
│   │                  (+ PreviousRunsApi pour le bootstrap J+1 du biais)
│   ├── mapper/      ← DTO ↔ domain
│   ├── local/       ← Room (ForecastCache, ClimateNormals, ForecastSample, ObservationSample)
│   ├── preferences/ ← DataStore
│   ├── repository/  ← Implémentations (dont le coalescing des fetches concurrents)
│   └── worker/      ← BiasRefreshWorker + Scheduler (WorkManager, fenêtre 24h)
├── domain/
│   ├── model/       ← Modèles métier (City, ForecastSeries, WeatherModel, ModelFamily,
│   │                  Coverage, DayNormals, HourlyConfidenceBand, WeatherCondition,
│   │                  BiasSample, ModelBias, BiasVariable, BiasSignificance…)
│   ├── repository/  ← Interfaces (dont BiasSampleRepository)
│   └── usecase/     ← ConfidenceCalculator, ComputeBiasUseCase,
│                      FetchBiasObservationsUseCase, BootstrapBiasHistoryUseCase,
│                      weighting strategies
├── ui/
│   ├── citylist/    ← Accueil : cartes favorites compactes, scénarios repliables, heatmap 12 h
│   ├── citydetail/  ← Détail d'une ville : cartes, chart, tableaux
│   │   └── confidence/  ← Écran "Pourquoi cette confiance ?"
│   ├── settings/    ← Paramètres (modèles avec tri, thème, langue)
│   ├── components/  ← Composables réutilisables (WeatherIcon, WindArrow, ShimmerBox…)
│   ├── accessibility/ ← Formatage des descriptions TalkBack
│   ├── theme/       ← Couleurs, typographie, tokens M3
│   └── navigation/  ← Routes et NavHost
└── widget/          ← MeteoWidget (Glance), config activity, splitter loader
```

## Modèles supportés

Listés dans `WeatherModel.kt` avec leur résolution native (km), leur horizon, leur zone de couverture, et leur institution source (`ModelFamily`).

| Modèle             | Résolution | Couverture       | Horizon | Institution         | Par défaut |
|--------------------|------------|------------------|---------|---------------------|:----------:|
| AROME France HD    | 1.5 km     | France           | 2 j (48 h) | Météo-France        |     ✓      |
| AROME France       | 2.5 km     | France           | 2 j (48 h) | Météo-France        |            |
| ARPEGE Europe      | 11 km      | Europe           | 4 j     | Météo-France        |     ✓      |
| ARPEGE World       | 25 km      | Global           | 4 j     | Météo-France        |            |
| ICON-EU            | 7 km       | Europe           | 5 j     | DWD (Allemagne)     |     ✓      |
| ICON               | 11 km      | Global           | 7,5 j   | DWD                 |            |
| ICON-D2            | 2 km       | Europe centrale  | 2 j     | DWD                 |            |
| GFS                | 13 km      | Global           | 16 j    | NOAA (USA)          |     ✓      |
| ECMWF              | 25 km      | Global           | 15 j    | ECMWF (UE)          |     ✓      |
| ECMWF AIFS         | 28 km      | Global (**IA**)  | 15 j    | ECMWF               |     ✓      |
| UKMO Global        | 10 km      | Global           | 7 j     | UK Met Office       |     ✓      |
| GEM Global         | 15 km      | Global           | 10 j    | ECCC (Canada)       |            |
| **HRRR**           | **3 km**   | USA continental  | 18 h standard (48 h sur 00/06/12/18Z) | NOAA |            |
| **MET Nordic**     | **1 km**   | Scandinavie      | 2,5 j   | MET Norway          |            |
| **HARMONIE**       | **5.5 km** | Europe           | 2,5 j   | KNMI (Pays-Bas)     |            |
| **BOM ACCESS**     | 15 km      | Global           | 10 j    | Bureau of Meteorology (Australie) |            |
| **CMA GRAPES**     | 15 km      | Global           | 10 j    | China Meteorological Administration |            |

Les modèles marqués "Par défaut" sont activés dès la première ouverture ; les autres sont activables dans les Settings, désormais **triables par zone, par famille ou par finesse** (résolution native).

**Diversité éditoriale** du catalogue :

- **ECMWF AIFS** est un modèle de prévision fondé sur l'IA/ML — il apporte un scénario méthodologiquement différent de l'IFS physique, sans recevoir de poids supérieur a priori
- **HRRR** est le pendant américain d'AROME HD : rapid-refresh 3 km, particulièrement utile pour la convection estivale sur les États-Unis
- **MET Nordic** offre la résolution la plus fine du catalogue (1 km) sur la Scandinavie — cousin arctique d'AROME HD
- **KNMI HARMONIE** partage le moteur numérique d'AROME mais avec une initialisation via l'IFS ECMWF — utile pour repérer un désaccord de conditions initiales sur l'Europe de l'Ouest
- **BOM ACCESS** et **CMA GRAPES** ajoutent une diversité méthodologique non-occidentale — sources indépendantes de biais éventuels du pool européen/nord-américain

### Note sur AROME HD et les variables dérivées

AROME France HD conserve un jeu de champs natifs plus réduit que certains autres modèles. La documentation Open-Meteo définit `weather_code` comme une variable dérivée pour AROME/ARPEGE, mais le client tolère également les réponses opérationnelles où `weather_code` ou `cloud_cover` total sont absents pour AROME HD.

Le client utilise donc en priorité le `weather_code` fourni par l'API. S'il manque, il infère d'abord pluie/neige depuis les précipitations et la température du **même modèle**. Pour les situations sèches où `cloud_cover` total manque aussi, les couches `cloud_cover_low`, `cloud_cover_mid` et `cloud_cover_high` servent à construire un indicateur de nébulosité de secours (maximum des couches) uniquement pour choisir l'icône clair/nuageux/couvert. Cette icône reste visuellement marquée comme inférée. Les données d'un modèle voisin ne sont jamais copiées pour « compléter » une cellule.

Les badges "%" restent conditionnés à la présence réelle de leur variable : une probabilité de précipitation absente n'est jamais transformée en 0 %, et une couverture nuageuse absente n'est jamais inventée.

## Indice de confiance

`ConfidenceCalculator` agrège les prédictions multi-modèles en un score 0-100 par variable pour chaque jour ET pour chaque heure.

**Algorithme** :

- Pour les variables continues (T, vent, précipitations) : moyenne et écart-type **pondérés** par `ModelWeightingStrategy`. L'écart-type est transformé en indice d’accord 0–100 via des seuils heuristiques (`tight`/`wide`) propres à chaque variable.
- Pour la pluie journalière : agreement binaire d'abord (les modèles s'accordent-ils sur *l'occurrence* ?), puis spread sur l'intensité si oui. Distinct de la bande horaire précipitation qui garde une représentation continue.
- Pour la condition météo actuelle : vote pondéré par famille (CLEAR/OVERCAST/RAIN…) — moyenner des codes WMO catégoriels n'a aucun sens. Tie-break : la condition la plus sévère.
- Pour la couverture nuageuse "maintenant" (utilisée par les cards home/détail) : moyenne pondérée horaire sur les modèles qui exposent `cloud_cover`.
- Pondération de production : **poids égaux**. La résolution de grille n’est pas utilisée comme proxy de qualité ; le point d’extension `ModelWeightingStrategy` n’autorise une pondération différente que si elle est un jour justifiée par un backtest vérifié.

```kotlin
val daily = calculator.dayConfidence(forecast, LocalDate.now())
val hourlyTemp: List<HourlyConfidenceBand> = calculator.hourlyTemperatureConfidence(forecast)
val hourlyPrecip: List<HourlyConfidenceBand> = calculator.hourlyPrecipitationConfidence(forecast)
val hourlyWind: List<HourlyConfidenceBand> = calculator.hourlyWindConfidence(forecast)
val currentCondition: WeatherCondition? = calculator.currentWeatherCondition(forecast)
val currentCloudCover: Int? = calculator.currentCloudCover(forecast)
val matrix: List<DayConditionsRow> = calculator.dailyConditionsByModel(forecast)
```

`DayConditionsRow.extrasByModel` porte les métadonnées par cellule (probabilité de pluie max journalière, couverture nuageuse moyenne journalière) qui alimentent les badges "%" sous les icônes.

La **TodaySummaryCard** conserve un résumé immédiatement lisible mais détaille désormais chaque variable dans quatre mini-cartes de même hauteur. La valeur centrale correspond à la moyenne inter-modèles ; les informations secondaires explicitent la plage, le spread, les rafales éventuelles ou la répartition des scénarios de pluie, avec l’accord propre à la variable.

**Bande de confiance multi-métriques** : le composant `ConfidenceBandSection` encapsule un sélecteur segmenté à 3 états (Température / Précipitations / Vent) au-dessus d'un graphe unique. Les 3 séries de bandes sont pré-calculées dans le ViewModel — la transition entre métriques est instantanée. La bande **température** superpose les **repères ERA5 Tmax/Tmin sur 10 ans**, chargés depuis l'API archive d'Open-Meteo et cachés 180 jours dans Room. Les graphes pluie/vent n'affichent pas de repère journalier sur une série horaire afin d'éviter de comparer des fenêtres temporelles différentes. Le graphique est **zoomable au pincement** sur l'axe temps (pinch à 2 doigts + pan) et **réinitialisable au double-tap**.

Les seuils actuels sont des **heuristiques de présentation**, pas une calibration scientifique ni une probabilité de justesse. Une future calibration prédictive devrait s’appuyer sur un corpus de vérification par variable, zone et échéance.

## Page "Pourquoi cette confiance ?"

Un clic sur le badge de confiance (en haut à droite de la carte "Aujourd'hui") ouvre une explication détaillée qui compose l'**edge éditorial** de l'app :

1. **Résumé du jour** avec verdict en langage naturel ("les modèles convergent fortement", "désaccord significatif"…)
2. **Une carte par variable** (température max, min, précipitations, vent) montrant :
    - Le résumé inter-modèles (valeur unique si convergence, plage si dispersion)
    - Le tableau modèle par modèle avec code couleur identique aux graphes de comparaison
    - La résolution de chaque modèle contribuant à ce jour
    - Une phrase d'interprétation qui traduit les chiffres en sens
3. **Section éducative "Pourquoi les modèles diffèrent ?"** : paragraphe pédagogique sur la résolution + tableau des modèles ayant réellement contribué + astuce AROME HD vs GFS/ECMWF

## Suivi de biais par modèle

L'app évalue en continu la précision de chaque modèle sur chaque ville favorite en croisant :

- **Les prévisions passées** — reconstruites via **Previous Runs** à échéance fixe J+1 (`_previous_day1`) ; le bootstrap manuel tente jusqu’à 21 jours, le cycle quotidien ne recharge qu’une courte fenêtre pour rester idempotent et peu coûteux
- **La référence historique** — récupérée par le `BiasRefreshWorker` depuis l’Historical Weather API d’Open-Meteo et stockée dans Room. C’est une réanalyse / donnée historique modélisée et assimilée, **pas une observation de station au point exact**

Pour chaque paire `(modèle, variable)`, `ComputeBiasUseCase` calcule sur une fenêtre glissante 30 jours :
- Écart moyen (arithmétique, dédup par date)
- Écart-type (Bessel n−1)
- Direction (WARM / COLD / NEUTRAL) et significativité (HIGH / MODERATE / NOT_SIGNIFICANT) basées sur des seuils absolus + ratios par variable

**Trois états visuels** dans le tableau prévisions, footprint vertical identique pour préserver l'alignement des noms de modèle entre colonnes :

| État | Rendu | Sémantique |
|---|---|---|
| **Biais significatif** | Chip coloré rouge/bleu + flèche + valeur signée | Le modèle sur/sous-estime — utile de le corriger mentalement |
| **Biais faible** | Chip gris neutre + coche + petite valeur signée | Le biais moyen signé est faible sur la fenêtre observée ; cela ne garantit pas la fiabilité globale du modèle |
| **En attente** | Pastille vide avec dash | < 14 jours de recouvrement, pas assez de données |

Clic sur un chip (les deux premiers états) ouvre une sheet avec **sparkline 30 jours** superposant la prévision et l'observation, une grille de stats et un texte contextuel adapté à l'état. Pour un biais signé faible, la sheet reste volontairement prudente : elle ne transforme pas l’absence de biais moyen en garantie de précision générale.

**Coalescing des fetches** : le `ForecastRepositoryImpl` dédoublonne les requêtes HTTPS concurrentes pour la même `(city, models, forecastDays)` via un registre `Deferred` sur un `SupervisorJob` du repo. Quand CityList, CityDetail et le widget cold-start-refreshent Paris en parallèle, une seule requête part réellement.

## Widgets homescreen

Widget Glance redimensionnable en 4 tailles :

- **2×1** : condition + T° actuelle + badge confiance
- **3×1** : + nom de la ville
- **4×1** : + min/max du jour
- **4×2** : + une ligne du bas configurable parmi 5 modes :
    - 4 prochaines heures (comportement historique, défaut)
    - 4 prochains jours
    - Mini bande de confiance **température** avec valeurs par jour
    - Mini bande de confiance **précipitations** avec valeurs par jour
    - Mini bande de confiance **vent** avec valeurs par jour

Les modes confidence rendent une heatmap horizontale colorée par la confiance sur 7 jours, avec sous chaque cellule la valeur agrégée et le jour de la semaine. C'est le rendu widget de la bande de confiance de l'écran détail.

**Localisation widget** : l'écran de config et le rendu du widget utilisent tous deux le helper `applyPersistedLocale` (dans `core/locale/`) pour respecter la préférence de langue de l'app, indépendamment de la locale système.

## Batching multi-modèles

Depuis la refonte réseau, l'app fait **1 seule requête HTTPS** pour récupérer les N modèles activés, au lieu de N requêtes parallèles. `OpenMeteoApi.getForecastBatched` demande `?models=meteofrance_arome_france_hd,meteofrance_arpege_europe,ncep_gfs_seamless,…` et Open-Meteo répond avec les variables suffixées par la clé du modèle (`temperature_2m_meteofrance_arome_france_hd`, `temperature_2m_meteofrance_arpege_europe`, …).

Le `BatchedForecastSplitter` décompose la réponse en un `ForecastResponseDto` par modèle, transparent pour le reste de la chaîne (mapper et cache Room inchangés — chaque modèle a toujours sa propre ligne cache). Un log DEBUG dédié `MeteoCompare/Net` permet de vérifier l’invariant en développement via Logcat.

Le batching réduit surtout le nombre de connexions, handshakes TLS et réveils radio. Aucun gain chiffré n’est annoncé sans benchmark reproductible.

## Premier lancement

1. Ouvrir le projet dans Android Studio.
2. Sync Gradle (le wrapper sera téléchargé automatiquement la première fois).
3. Lancer sur émulateur API 27+ ou device.

Aucune clé API n'est nécessaire — Open-Meteo est gratuit pour usage non commercial.

## Tests

```bash
./gradlew testDebugUnitTest             # tests JVM rapides
./gradlew connectedDebugAndroidTest     # UI, navigation, Room et DataStore sur appareil
./gradlew lintDebug assembleDebug        # analyse statique + compilation APK
```

La suite instrumentée utilise des repositories Hilt factices : aucune requête
Open-Meteo n'est effectuée pendant `androidTest`. Elle couvre les parcours de
navigation, les états Compose, l'accessibilité, la configuration widget, les
DAO Room en mémoire, DataStore et la locale persistée.

La stratégie complète, les règles de stabilité et les commandes Windows sont
documentées dans [`TESTING.md`](TESTING.md).

## Accessibilité

Toutes les zones interactives ont des `contentDescription` lisibles par TalkBack :

- Les **cartes de villes** annoncent un résumé fluide qui commence par la condition actuelle : "Ville Paris, Île-de-France. Ensoleillé. Actuellement 20 degrés. Température entre 22 et 24 degrés, confiance haute, 85 pourcent."
- Le **badge de confiance** est annoncé comme bouton : "Confiance 85%, ouvrir l'explication détaillée"
- Les **graphiques Canvas** (bande horaire) ont des descriptions générées par `A11yFormatter` qui résument les données clés. La bande de confiance annonce en plus son état de zoom ("Graphique zoomé, double-tap pour réinitialiser") pour rester compréhensible quand l'utilisateur zoome sans voir l'écran.
- Les **titres de section** sont marqués `heading()` pour permettre la navigation par titre.

Le module `ui/accessibility/A11yFormatter.kt` centralise les chaînes pour garder une terminologie cohérente.

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
- ✅ v0.2 — Icônes de temps, tableau Jour × Modèle des conditions, ajout UKMO / AIFS / GEM / ICON-D2
- ✅ v0.3 — Highlight du jour courant dans les tableaux, correction de bugs
- ✅ v0.4 — Toggle "par heure / par jour", zoom pincé sur la bande de confiance, badges probabilité de pluie et couverture nuageuse sous les icônes, direction du vent avec flèches downwind, indicateur "mis à jour il y a X", icône composite "partiellement nuageux" (soleil + nuage bi-color), titres du vent clarifiés ("moyenne à 10m" au lieu de "max" ambigu)
- ✅ v0.5 — Nouvelles données (probabilité de pluie, couverture nuageuse, vent) et correction de bugs
- ✅ v0.6 — Widget homescreen (Glance) redimensionnable 2×1 / 3×1 / 4×1 avec opacité de fond configurable et sélection de ville favorite ; reproduit un résumé compact de la TodaySummaryCard
- ✅ v0.7 — Optimisation batterie et CPU pour l'application et widget (WorkManager pour le refresh widget, réduction des recomputes), upgrade de la stack, amélioration des widgets, mise à jour Kotlin 2.x
- ✅ v0.8 — Batching multi-modèles (1 requête HTTPS au lieu de N), bande de confiance multi-métriques (T° / pluie / vent), repères ERA5 sur 10 ans en overlay, tri des modèles Settings (zone/famille/finesse), ajout HRRR / MET Nordic / KNMI HARMONIE / BOM ACCESS / CMA GRAPES (5 nouveaux modèles), widget 4×2 avec mode bande de confiance, i18n des widgets
- ✅ v0.9 — Correction des requêtes dupliquées, correction des widgets fantômes, widgets et application partagent le même espace de données, amélioration des widgets, ajout des heatmaps pour les tableaux par heure
- ✅ v1.0 — Suivi de biais par modèle et par ville : historique J+1 reconstruit via Previous Runs, références de réanalyse via WorkManager, chip 3 états dans les tableaux (biais significatif / biais faible / en attente), sheet dédiée avec sparkline 30j et texte contextuel, coalescing des fetches HTTP concurrents
- ✅ v1.1 — Refonte des widgets, ajout des icônes partagées, optimisation globale de l'application, correction de bugs
- ✅ v1.2 — Amélioration des widgets, refonte des tableaux (thème et trie), couleurs de modèles par famille, optimisation énergétique (worker, requête, etc.), correction de bugs
- ✅ v1.3 — Amélioration des widgets, amélioration visuelle de la page details, ajout du classement des modèles par localité, refonde de la page de biais, correction de bugs
- ✅ v1.4 — Améliorations des tableaux, ajout d'une timeline, ajout des sections retractables, correction de bugs
- ✅ v1.5 — Refonte de la page "city details", amélioration du widget mini forecast, correction de bugs
- ✅ v1.5.1 — Uniformisation des tableaux, amélioration des tailles texte / icône dans les widgets, correction de bugs
- ✅ v1.6 — Section chronologie et "A retenir", correction de bugs
- ✅ v1.6.1 -> v1.6.4 — Amélioration des sections chronologie et "A retenir", ajout d'un widget "A retenir", correction de bugs
- ✅ v1.7.0 — Refonte des interfaces, ajout de la donnée « rafale », ajout des scénarios, correction du calcul sunrise / sunset, TodaySummary enrichie, cartes Home compactées avec scénarios repliables et nouvelle heatmap 12 h, correction de bugs
- ✅ v1.8.0 — Évolution des prévisions par snapshots locaux ~24/~48/~72 h, sans requête réseau additionnelle, cohorte commune de modèles, âge réel affiché, carte repliable mémorisée par ville, détails modèle par modèle et signaux injectés dans « À retenir » ; localisation FR/EN/ES/DE/IT
- ✅ v1.9.0 — Moteur de prévisions v2 et ajout des informations "marine" pour les villes cotières, correction de bugs

## Licence

[Apache License 2.0](LICENSE) — vous pouvez utiliser, modifier et redistribuer le code librement, à condition de conserver la notice de copyright.

Les données météo sont fournies par [Open-Meteo](https://open-meteo.com) (également open-source, AGPL-3.0). Les modèles eux-mêmes sont produits par leurs organismes respectifs : Météo-France (AROME, ARPEGE), DWD (ICON, ICON-D2), NOAA (GFS, HRRR), ECMWF (IFS et AIFS), UK Met Office (UKMO), Environnement et Changement climatique Canada (GEM), MET Norway (MET Nordic), KNMI (HARMONIE), Bureau of Meteorology Australie (ACCESS), China Meteorological Administration (GRAPES).
