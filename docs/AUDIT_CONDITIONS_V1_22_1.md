# Audit et consolidation des conditions météorologiques — MeteoCompare 1.22.1

## Symptômes rapportés

Pour Gif-sur-Yvette, certaines cellules des vues Chronologie (daily jour/nuit),
Chronologie horaire et Chart View n'affichent pas de condition.

**Limite de la vérification** : le code source a été audité. Aucune réponse API
horodatée ni cache réellement observé pour Gif-sur-Yvette n'a été fourni ; on
ne peut donc pas quantifier les cellules corrigées sur cette ville.

## Causes repérées dans le code

1. `ForecastSeriesDaily.resolveDailyCondition` refusait un résultat en cas de
   trou temporel >3 h, **même lorsque 17/24 ou 19/24 heures indiquaient
   exactement le même type de temps**, ce qui rendait cette dominante
   mathématiquement certaine indépendamment des heures manquantes.
2. `BatchedForecastSplitter.resolveCloudCover` reconstruisait le couvert à
   partir des trois couches **seulement si la liste entière de couverture
   totale était absente** : une valeur nulle ponctuelle demeurait nulle même
   lorsque les couches du même instant étaient disponibles.
3. Les anciens caches possédant des couches nuageuses mais pas de total pouvaient
   perdre leur prévision de condition dans certains agrégats, même quand un
   `HourlyForecast.resolveHourlyCondition` par modèle pouvait la reconstruire.
4. `WeatherConditionConsensus.resolveAggregate` conservait `null` sur une
   égalité WMO entre deux familles natives, **sans examiner deux autres familles
   indépendantes** ayant produit une condition de repli cohérente.
5. Les conditions `null` deviennent `WeatherCondition.UNKNOWN` dans Chart View.
   Une vraie opposition entre familles de modèles peut donc afficher « ? » :
   le code ne constitue pas à lui seul la preuve d'un bug de l'API météo.

## Correctifs appliqués

- `CloudCoverReconstruction.resolve` : source unique, **par heure**, total
  original prioritaire, repli seulement si les trois couches sont valides.
  Aucune couche manquante n'est implicitement remplacée par 0.
- `HourlyForecast.effectiveCloudCover` : même règle pour réponses nouvelles et
  anciens caches ; utilisé pour les snapshots horaires, le résumé actuel,
  le mini-prévisionnel et les moyennes journalières/périodes.
- Résolution des conditions quotidiennes : une classe météorologique présente
  pendant strictement plus de la moitié des heures *réelles* de toute la période
  est démontrablement dominante malgré des trous internes. Les heures sont
  dédoublonnées en instants absolus (DST conservée). La simple majorité de la
  famille « sec » ne suffit PAS à déduire le sous-type « ciel clair ».
- Agrégat horaire multi-modèles : si deux familles WMO s'opposent exactement,
  on peut utiliser les conditions calculées de deux AUTRES familles indépendantes.
  Une majorité WMO native non ambiguë reste prioritaire. L'accord catégoriel
  hybride n'est jamais présenté comme un pourcentage de confiance calibré.
- Agrégat horaire sans WMO et sans variables centrales : conserver une
  estimation catégorielle par modèle lorsqu'elle existe ; ne pas convertir les
  oppositions sèches/humides en résultat certain.

## Ce qui reste volontairement indéterminé

- Découpage jour/nuit sans lever/coucher fiables ; fin de l'horizon si le lever
  suivant n'est pas disponible.
- Une ou deux heures de données pour représenter une nuit complète.
- Absence de code WMO et absence de variables permettant une interprétation.
- Opposition parfaite de familles météorologiques indépendantes.
- Plusieurs sous-types sans majorité mathématiquement certaine et séparés par
  de grandes lacunes.

Il n'y a **aucune interpolation météo arbitraire** entre heures ou entre modèles.
La formule historique `0,9*bas + 0,6*moyen + 0,3*haut`, conservée par
compatibilité avec v1.22.1, reste une **approximation logicielle** : elle ne
constitue pas une loi physique universelle de chevauchement nuageux. Si le
fournisseur fournit `cloud_cover`, cette valeur a toujours priorité.

Le diagnostic de neige à partir de la température au sol seule est aussi
une *heuristique* ; il ne remplace pas la structure thermique verticale.
Un code WMO natif reste prioritaire pour la famille de précipitation.

**Alignement temporel à expertiser séparément** : la documentation
Open-Meteo définit `weather_code` comme instantané tandis que
`precipitation` et `precipitation_probability` se rapportent à
**l'heure précédente**. Les replis de condition de la v1.22.1
associent ces variables au même index. Ce choix nécessite de fixer
explicitement une convention d'affichage (« heure terminée » vs
« heure à venir ») et des tests sur les transitions pluvieuses ; il
n'est pas changé ici pour éviter une régression d'alignement des vues.

## Distinction importante à préserver dans la future interface

`daily.weather_code` d'Open-Meteo est documenté comme **l'état le plus sévère
au cours de la journée**. Lorsqu'il sert de repli en l'absence de couverture
horaire représentative, il ne prouve **pas** une météo *dominante*.
L'interface devrait expliciter :

- `WMO horaire` : observation de modèle de l'heure visée ;
- `Dominante agrégée` : déterminée par les heures disponibles, avec couverture ;
- `WMO quotidien` : phénomène maximal, non durée ;
- `Estimé` : valeurs reconstruites et incertitude ;
- `Indéterminé` : absence de données vs modèles en désaccord.

Ajouter ces statuts en UI demanderait une évolution des modèles de présentation
et des traductions, non incluse dans le correctif ciblé ci-joint.

## Tests et niveau de vérification

- **68 tests de logique météo du dépôt : réussis**, en exécutant les deux
  classes `ForecastSeriesConditionResolutionTest` et
  `WeatherConditionConsensusTest` via le compilateur Kotlin et un relais
  minimal des assertions JUnit.
- Cas ajoutés : cache nuageux sans total, trou 5–7 h avec dominante certaine,
  famille sèche sans sous-type majoritaire, résolution d'une égalité native
  par deux lignées indépendantes, absence d'arbitrage avec une seule lignée,
  repli catégoriel de caches, préservation de l'incertitude.
- Un test spécifique ajouté dans `BatchedForecastSplitterTest` vérifie les
  valeurs `cloud_cover` nulles au milieu d'une liste ; son exécution dépend de
  la chaîne Android/Gradle.
- **Limite** : impossible de lancer `./gradlew :app:testDebugUnitTest` ; la
  distribution Gradle 9.7.1 n'est pas installée et cet environnement ne peut
  pas atteindre le serveur de téléchargement. Les tests UI/instrumentés et le
  build APK restent à exécuter dans Android Studio / CI.

## Recommandations de validation terrain

1. Capturer les réponses Open-Meteo et l'échéance par modèle pour
   Gif-sur-Yvette, et comparer avant/après à données strictement identiques.
2. Mesurer, par heure et par période, les sorties `WMO`, `dérivé`,
   `égalité inter-modèles`, `couverture insuffisante`, `aucune donnée`.
3. Rejouer 7–14 jours de prévisions sauvegardées, mesurer le nombre de cellules
   sans condition, puis vérifier systématiquement les éventuelles nouvelles
   conditions (surtout pluie, neige et verglas).
4. Lancer les tests Android et Compose avec le pipeline GitHub Actions.

Références : https://open-meteo.com/en/docs ; recommandations de documentation
sur les variables horaires et l'agrégation du `weather_code` quotidien.
