# Radar pluie Android

Cette implémentation porte dans l’application Android le radar pluie de la version web de MeteoCompare, sans WebView.

## Parité fonctionnelle

- métadonnées et images radar RainViewer (`weather-maps.json`, rendu 512 px, palette `2/0_1`) ;
- fond cartographique OpenStreetMap ;
- trois portées identiques au web : Proche (`mapZoom=9`, `radarZoom=7`, `scale=4`), Régional (`8/7/2`) et Large (`6/5/2`) ;
- animation des observations récentes à 700 ms par trame avec sélection manuelle de la trame ;
- analyse canonique au zoom radar 7 sur les 7 dernières trames consécutives ;
- projection unique à +15, +30, +45 ou +60 minutes ;
- segmentation des cellules de pluie, advection par recouvrement d’empreinte, régression du centroïde, rejet des mouvements aberrants et plafond de vitesse ;
- projection de la position, de la déformation/croissance/dissipation, de la probabilité de survie et de l’incertitude ;
- trajectoire observée, trajectoire projetée, contour projeté et enveloppe probable ;
- score d’impact pour la localité et identités de cellules stables lors des changements de portée ;
- couverture périphérique basse résolution en portée Large ;
- recalcul manuel avec rafraîchissement des métadonnées et conservation de la dernière projection utilisable en cas d’échec ;
- plein écran et persistance des choix mode/portée/horizon/plein écran via `SavedStateHandle` ;
- accès depuis la fiche d’une localité **et directement depuis le menu `⋮` de sa carte sur la Home**, sur téléphone et tablette.

En mode Projection, la dernière observation RainViewer reste visible avec une opacité `0.38`, contre `0.80` en observation, exactement comme sur le web. Le calcul du nowcast reste local à l’appareil ; RainViewer ne fournit que les observations.

## Architecture

- `domain/radar/RadarModels.kt` : modèles et constantes partagés.
- `domain/radar/RainRadarNowcast.kt` : algorithme pur Kotlin, sans dépendance Android.
- `data/radar/RadarRepository.kt` : RainViewer, OpenStreetMap, validation des URL et caches mémoire.
- `di/RadarModule.kt` : binding Hilt.
- `ui/radar/RadarViewModel.kt` : orchestration, lecture, analyse, recalcul et état persistant.
- `ui/radar/RadarScreen.kt` : UI Compose et rendu `Canvas` natif.

L’algorithme est volontairement isolé du framework Android pour être déterministe et testable sur la JVM.

## Audit réseau et performance

Les points suivants ont été renforcés après l’audit de la première intégration :

- les appels OkHttp sont désormais réellement annulables : l’annulation d’une coroutine annule le `Call`, et une réponse arrivée après annulation n’est pas lue/décompressée ;
- deux demandes simultanées de la même URL sont coalescées par verrou d’URL puis re-vérification du cache, ce qui évite un double téléchargement entre affichage et analyse ;
- les métadonnées RainViewer conservent un TTL de 5 minutes hors recalcul forcé ;
- les 7 trames utilisées pour le nowcast sont téléchargées/décodées **séquentiellement**, comme sur le web, au lieu de provoquer une rafale de 7 travaux concurrents ;
- les tuiles OSM sont choisies à partir du viewport réellement visible, avec la même géométrie Web-Mercator que le web, au lieu d’une grille fixe 5×5 ;
- aucune tuile OSM n’est demandée avant que Compose n’ait communiqué la taille réelle de la carte ;
- renvoyer exactement le même viewport n’entraîne pas un nouveau chargement ;
- changer seulement l’horizon +15/+30/+45/+60 est purement local et ne déclenche aucun appel repository ;
- caches bornés à 16 images radar et 32 tuiles OSM, soit environ 24 Mio de pixels ARGB au maximum (contre ~52 Mio dans la première livraison auditée).

Pour Paris au zoom cartographique 9, la sélection de tuiles produit 6 tuiles pour un viewport 360×360 et 20 pour 1200×800, contre 25 systématiques auparavant.

Un harness Kotlin autonome sur 7 observations de 512×512 avec trois cellules donne, dans l’environnement de génération, un temps médian d’environ **27.4 ms** et un p90 d’environ **31.6 ms** pour le suivi/projection pur. Ce chiffre est un contrôle de non-régression CPU, pas un benchmark Android GPU/UI officiel.

## Conformité avec la version web

La suite de référence web `node tools/run-tests.mjs --feature radar` passe : **10 fichiers de tests sur 10**.

Un contrat numérique Android utilise exactement les mêmes sept observations synthétiques que le web et vérifie à `1e-12` près :

- `vx = 0.4000000000000001`, `vy = 0` ;
- confiance mouvement `0.9630623120966285` ;
- confiance d’évolution `0.7327826913976472` ;
- déplacements projetés `dx` à +15/+30/+45/+60 ;
- incertitudes correspondantes ;
- facteurs de forme/surface et survie ;
- fenêtre d’impact local +15 → +60 et ses probabilités.

Le même contrat exécuté via un harness Kotlin autonome passe (`RADAR_CORE_CONTRACT_OK`). Les tests de domaine couvrent aussi les filtres couleur, segmentation, contours, outliers, croissance/dissipation, mouvement vers/loin de la localité, scission, stabilité des identités et couverture Large.

## Couverture de tests Android

Tests dédiés présents dans le projet après audit :

- **31 tests JVM radar** : 6 repository/réseau, 16 domaine/algorithme, 9 ViewModel ;
- **7 tests Compose dédiés à `RadarContent`** ;
- **3 tests instrumentés supplémentaires** pour l’accès radar : menu de carte Home, navigation directe Home → Radar et navigation fiche ville → Radar.

Les nouveaux tests de non-régression vérifient notamment l’annulation du `Call` OkHttp, la sélection OSM par viewport, les bornes de cache, le chargement séquentiel des sept trames, l’absence de réseau lors d’un simple changement d’horizon, les opacités web, la désactivation du recalcul pendant l’analyse et le chemin direct depuis la Home.

Commandes de validation dans un environnement Android complet :

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

## Statut de validation dans l’environnement de génération

Exécuté avec succès ici :

- suite radar web : **10/10 fichiers** ;
- compilation/exécution autonome du cœur Kotlin et contrat numérique web↔Android : **OK** ;
- harness de performance CPU du cœur : **OK**, médiane ~27.4 ms, p90 ~31.6 ms ;
- validation XML des ressources : **OK**.

La suite Gradle Android complète n’a pas pu être exécutée ici. Le wrapper demande Gradle 9.7.1, absent du cache, et l’environnement ne peut pas résoudre `services.gradle.org` (`UnknownHostException`), y compris avec `--offline`. Les tests JVM/Compose Android sont donc bien présents et contrôlés statiquement, mais ils doivent encore être exécutés avec les deux commandes ci-dessus dans l’environnement Android habituel avant merge.
