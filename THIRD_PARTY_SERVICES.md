# Services tiers utilisés par MeteoCompare

*Vérifié pour la release 1.17.0 le 7 octobre 2026.*

Ce document complète `PRIVACY.md`. Il sert de **gate de release** pour les services
réseau dont les conditions ou politiques d'usage peuvent évoluer indépendamment du
code de MeteoCompare.

## RainViewer — radar pluie

MeteoCompare utilise l'API publique RainViewer uniquement lorsque l'utilisateur ouvre
explicitement l'écran radar.

- API : `https://api.rainviewer.com/public/weather-maps.json`
- Tuiles/images : hôte HTTPS retourné par l'API et limité par le code aux sous-domaines
  `*.rainviewer.com`.
- Attribution visible dans l'écran radar : **« Radar météo par RainViewer »**, avec
  lien vers `https://www.rainviewer.com/`.
- Les images radar déjà nécessaires à l'affichage/au nowcast sont conservées dans un
  cache mémoire borné ; aucune prélecture régionale ou téléchargement hors-ligne n'est
  effectué.

Conditions à vérifier avant chaque release :

- documentation API : `https://www.rainviewer.com/api.html` ;
- conditions générales : `https://www.rainviewer.com/terms.html`.

Au 7 octobre 2026, la documentation présente l'API publique gratuite comme destinée
aux usages personnels, éducatifs et aux petits projets communautaires, demande une
attribution visible et recommande la mise en cache. Elle indique de contacter
RainViewer pour les intégrations commerciales, les volumes élevés ou les usages
nécessitant une disponibilité garantie.

**Gate release :** si le modèle de distribution de MeteoCompare devient commercial,
si le trafic radar devient important, ou si les conditions RainViewer changent,
obtenir un accord/plan adapté ou désactiver/remplacer le fournisseur radar avant la
publication. Le caractère gratuit, open-source et sans fonctionnalités premium de
MeteoCompare ne dispense pas de cette vérification.

## OpenStreetMap — fond cartographique du radar

MeteoCompare utilise les tuiles raster officielles :
`https://tile.openstreetmap.org/{z}/{x}/{y}.png`.

Conformité implémentée :

- attribution visible **« © OpenStreetMap contributors »**, avec lien vers
  `https://www.openstreetmap.org/copyright` ;
- `User-Agent` stable et identifiable avec l'URL du projet ;
- cache HTTP disque OkHttp dédié (`cacheDir/radar-http`, 64 MiB) respectant les
  en-têtes `Cache-Control`, `Expires`, `ETag` et `Last-Modified` ;
- revalidation conditionnelle gérée par OkHttp après expiration ;
- chargement limité aux tuiles réellement visibles ; aucune prélecture ou fonction
  de téléchargement hors-ligne.

Politique à vérifier avant chaque release :
`https://operations.osmfoundation.org/policies/tiles/`.

**Gate release :** si le trafic devient significatif ou si les besoins ne peuvent
plus respecter la politique du serveur communautaire OSM, migrer vers un fournisseur
de tuiles OSM adapté ou une infrastructure propre avant publication.
