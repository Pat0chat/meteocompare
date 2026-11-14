# MeteoCompare — Conditions V3 : statuts, interfaces, vérification et entretien

**Socle** : code Android v1.22.1, moteur de conditions V2 préexistant.  
**Portée de cette livraison** : visualisation des diagnostics, transmission de la provenance, tests supplémentaires et suppression des chemins d'affichage obsolètes.

## 1. Contrat de présentation

| Statut | Représentation | Signification communiquée |
|---|---|---|
| `RELIABLE` | Icône normale | Source native ou consensus cohérent ; **pas une fiabilité probabiliste garantie** |
| `ESTIMATED` | Icône + `≈` | Condition inférée de variables d'un même modèle ou d'un consensus utilisant des estimations |
| `AMBIGUOUS` | Deux petites icônes alternatives + `≠` (ou `≠` seul si alternatives insuffisantes) | Plusieurs conditions crédibles sans dominante sûre |
| `INCOMPLETE` | Icône ou `—`, avec `◔` | Couverture temporelle partielle ; dominante démontrée ou décision sur échantillons représentatifs |
| `UNAVAILABLE` | `—` | Aucune condition météorologique exploitable |

L'appui sur chaque icône ouvre une fenêtre détaillée : condition, alternatives, provenance, pourcentage de couverture, taux d'accord non calibré, motifs d'indétermination, phénomène notable. Une légende explique les cinq états. Les icônes possèdent une description d'accessibilité.

## 2. Intégration des vues

- **Chronologie horaire** (`SimplifiedTimeline.kt`, `ChronoTimeline.kt`) : transmet `point.conditionDiagnostic` au composant unique.
- **Chronologie Daily jour/nuit** : transmet deux diagnostics distincts `dayConditionDiagnostic` et `nightConditionDiagnostic`; le calcul ne supprime pas une période connue seulement comme ambiguë ou incomplète.
- **Chart View** : icônes interactives sur l'heure sélectionnée et sur le ruban horaire, avec virtualisation sur le viewport pour les horizons jusqu'à 240 h.
- **Tableaux horaires et journaliers par modèle** : statut même lorsque la condition est absente ; indication de provenance homogène et légende partagée.
- **Traductions** : ressources dans les six dossiers `values`, `values-en`, `values-de`, `values-es`, `values-it`, `values-tr`. Certains textes explicatifs secondaires sont encore en anglais dans les quatre derniers catalogues ; une revue linguistique native est conseillée.

## 3. Consolidation des décisions

- Une période avec des heures manquantes et une dominante identifiée reste `INCOMPLETE` ; elle ne bascule pas vers `RELIABLE` ou `ESTIMATED`.
- Les diagnostics des modèles suivis par le consensus sont propagés aux périodes jour/nuit et aux points horaires.
- Les valeurs de température/précipitations médianes entre modèles ne servent plus de repli pour fabriquer une condition catégorielle ; l'API historique correspondante reste source-compatible.
- L'absence de données reste distincte d'un désaccord ; un accord des modèles n'est jamais présenté comme une probabilité calibrée de vérité.

## 4. Tests et résultats effectivement exécutés

| Ensemble | Résultat | Exécution |
|---|---:|---|
| Tests du noyau Kotlin | **98 / 98** | Exécutés localement avec Kotlin et classes JVM du dépôt |
| Corpus historique Daily | **8 461 cas, 0 différence** | Rejeu de la fixture v1 sur le moteur courant |
| Corpus statuts additionnel | **1 200 cas** | Rejoué dans les tests Kotlin (inclus dans les 98) |
| Tests Python du dépôt, y compris six nouveaux garde-fous V3 | **222 / 222** | `python -m unittest discover -s scripts/tests -q` |
| Ressources XML | **Six langues valides** | Parsing XML + garde-fous sur les identifiants |
| Tests Compose ajoutés | **4 tests** | Ajoutés au code, NON exécutés ici |
| Test Compose Chart View existant | **Adapté** | Assertion corrigée pour plusieurs icônes virtualisées, NON exécutée ici |
| Compilation du projet Android et tests instrumentés | **Non validés** | Gradle 9.7.1 absent ; l'URL de distribution ne se résout pas |
| Prévisions réelles Gif-sur-Yvette | **Non rejouées** | Pas de capture Open-Meteo historique à données identiques dans les fixtures fournies |

Le nouveau corpus balaie 4 fuseaux (`UTC`, `Europe/Paris`, `America/New_York`, `Asia/Tokyo`), 3 dates (dont des changements d'heure), deux sources (WMO natif et inféré), deux types de conditions (ciel clair et pluie) et toutes les quantités d'heures connues de 0 à 23/24/25. L'attendu repose sur la propriété mathématique `nombre de créneaux avec la condition > durée totale / 2`.

Les tests de l'ancien moteur couvrent en outre : prévalence WMO, égalités pluie/sec, doublons d'horodatages, périodes incomplètes, nébulosité, températures proches de 0 °C, indépendance des modèles.

## 5. Nettoyage effectué

- Un seul composant de diagnostic et de légende pour les écrans concernés.
- Suppression du marqueur ancien par atténuation alpha, incompatible avec une provenance explicitée.
- Retrait des calculs de médianes de température/précipitation devenus inutiles pour l'icône centrale.
- Retrait de l'ancien Canvas `StaticWeatherIconStrip` devenu inutilisé après adoption des icônes consultables.
- Actualisation du test de virtualisation Chart View : contrôle du **nombre d'icônes visibles**, non plus d'un unique Canvas.
- Correction de commentaires et des explications de couverture partielle.
- `__pycache__` et `.pyc` exclus des livrables.

## 6. Vérifications nécessaires sur Android avant publication

Depuis la racine du projet, avec JDK/SDK Android et un accès au Gradle Wrapper :

```sh
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

Vérifier en plus, sur appareil, les cinq statuts dans Chart View, chronologie horaire, chronologie Daily (jour/nuit), TalkBack, le défilement de 240 h et les traductions. Mesurer les éventuelles régressions de fluidité du ruban Chart View. Comparer les mêmes captures Open-Meteo pour Gif-sur-Yvette avant/après afin de mesurer la disparition des « trous » injustifiés, et conserver les trous justifiés.

Les statuts sont des **qualifications de données et de décision**, et non des estimations calibrées de la probabilité qu'une prévision se réalise. Un audit scientifique complet nécessiterait des observations historiques indépendantes pour mesurer précision et fiabilité.
