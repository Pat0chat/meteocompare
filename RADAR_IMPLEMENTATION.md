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
- plein écran et persistance des choix mode/portée/horizon/plein écran via `SavedStateHandle`.

En mode Projection, la dernière observation RainViewer reste visible mais atténuée afin que les formes projetées restent lisibles, comme sur le web. Le calcul du nowcast reste local à l’appareil ; RainViewer ne fournit que les observations.

## Architecture

- `domain/radar/RadarModels.kt` : modèles et constantes partagés.
- `domain/radar/RainRadarNowcast.kt` : algorithme pur Kotlin, sans dépendance Android.
- `data/radar/RadarRepository.kt` : RainViewer, OpenStreetMap, validation des URL et caches mémoire.
- `di/RadarModule.kt` : binding Hilt.
- `ui/radar/RadarViewModel.kt` : orchestration, lecture, analyse, recalcul et état persistant.
- `ui/radar/RadarScreen.kt` : UI Compose et rendu `Canvas` natif.

L’algorithme est volontairement isolé du framework Android pour être déterministe et testable sur la JVM.

## Tests ajoutés

Les tests JVM couvrent notamment le filtre couleur, le nettoyage du masque, la segmentation 4-connexe, les contours, l’advection, le rejet d’outliers, les horizons 15/30/45/60, la croissance/dissipation, l’impact local, les scissions, les identités stables, la couverture Large, la géométrie des portées, les URL RainViewer/OSM, le ViewModel et la restauration d’état.

Les tests instrumentés Compose couvrent les contrôles Observation/Projection, les quatre horizons, les portées, le plein écran, le recalcul, le cas d’une seule trame, l’action depuis la fiche ville et la navigation de bout en bout vers le radar avec un repository Hilt factice.

Commandes de validation dans un environnement Android complet :

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

## Note de validation dans l’environnement de génération

Le cœur Kotlin pur a été compilé séparément et exécuté avec un harness de comportement. La suite radar de référence du projet web passe également. Le build Gradle Android complet n’a pas pu être lancé ici car la distribution Gradle du wrapper doit être téléchargée et l’environnement d’exécution ne dispose pas de cet accès réseau/SDK Android ; il faut donc exécuter les deux commandes ci-dessus dans l’environnement Android habituel du projet avant merge.
