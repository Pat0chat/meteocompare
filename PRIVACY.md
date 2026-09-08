# Politique de confidentialité — MeteoCompare

*Dernière mise à jour : 8 septembre 2026*

## TL;DR

**MeteoCompare n'utilise pas vos données à des fins de profilage, de publicité ou
d'analytics.** L'application ne contient ni SDK publicitaire, ni analytics, ni
crash reporting, ni système de compte.

Les favoris, préférences et caches applicatifs sont stockés localement. Certaines
informations nécessaires au fonctionnement météo quittent toutefois l'appareil :
une recherche de ville est envoyée au service de géocodage Open-Meteo, et les
coordonnées des lieux consultés sont envoyées aux API météo Open-Meteo. Pour la
Vigilance française, un code de département peut être envoyé au Worker public
MeteoCompare.

---

## 1. Données traitées par MeteoCompare

MeteoCompare ne constitue pas de fichier d'utilisateurs et n'utilise aucune donnée
à des fins de profilage, analyse d'audience, publicité ou suivi inter-applications.

Concrètement :

- ❌ Pas de Google Analytics, Firebase Analytics ou SDK équivalent
- ❌ Pas de Crashlytics ou autre crash reporting distant
- ❌ Pas de publicité ni réseau publicitaire
- ❌ Pas d'Advertising ID (`AD_ID`)
- ❌ Pas de compte MeteoCompare
- ❌ Pas de cookies ou WebView de suivi
- ❌ Pas de fingerprinting volontaire de l'appareil
- ❌ Pas de suivi inter-applications

Les seules transmissions réseau effectuées par l'application sont celles nécessaires
aux fonctions décrites dans les sections 2 et 3.

## 2. Open-Meteo

MeteoCompare utilise les API publiques d'[Open-Meteo](https://open-meteo.com/)
pour la recherche de lieux, les prévisions météo, certaines données historiques
et les données marines.

### Informations envoyées à Open-Meteo

Selon l'action effectuée dans l'application :

- **Recherche d'une ville** : la chaîne de recherche saisie est envoyée à l'API
  de géocodage Open-Meteo.
- **Chargement ou actualisation d'un lieu** : les coordonnées du lieu consulté
  (latitude et longitude), les variables météo demandées, les modèles choisis et
  les paramètres techniques nécessaires à la requête sont envoyés aux API
  Open-Meteo.
- **Chargements automatiques ou widgets** : les coordonnées d'une ville favorite
  peuvent être réutilisées pour actualiser ses données météo sans que l'utilisateur
  ressaisisse la ville à chaque fois.

Comme pour toute connexion Internet, Open-Meteo reçoit également l'adresse IP
source et des informations HTTP techniques nécessaires à l'acheminement de la
requête.

MeteoCompare n'envoie pas à Open-Meteo de compte Google, d'identifiant publicitaire,
d'identifiant Android propre à l'application, de nom, d'adresse e-mail ou de liste
complète de favoris.

### Conservation côté Open-Meteo

La politique actuelle d'Open-Meteo indique que le service API gratuit peut conserver
des **journaux de serveur pendant 90 jours** à des fins de diagnostic, maintenance
et prévention des abus. Ces journaux peuvent notamment contenir des informations
sensibles présentes dans les requêtes, par exemple des **coordonnées géographiques**.
Open-Meteo indique que ces journaux sont supprimés après 90 jours et ne sont pas
partagés avec des tiers.

Cette conservation est effectuée par Open-Meteo selon ses propres conditions et
sa propre politique de confidentialité :
[open-meteo.com/en/terms](https://open-meteo.com/en/terms).

Les données obtenues via Open-Meteo sont fournies sous licence **CC BY 4.0**.
MeteoCompare les agrège, les compare et les transforme avant affichage. Attribution
et licence : [open-meteo.com/en/license](https://open-meteo.com/en/license).

## 3. Vigilance Météo-France via le Worker MeteoCompare

Pour les villes françaises, l'application peut interroger le Worker public
`https://meteocompare.app/_mcx/vigilance` afin d'afficher la Vigilance officielle
Météo-France.

L'application envoie uniquement les paramètres fonctionnels suivants :

- le **code du département** (par exemple `91`) ;
- `coast=1` lorsque la localité est identifiée comme côtière, afin d'inclure la
  vigilance littorale.

Le Worker gère côté serveur l'authentification Météo-France et le cache. Aucun
identifiant, mot de passe ou token Météo-France n'est embarqué dans l'application.

Comme pour toute requête HTTPS, l'infrastructure réseau qui héberge ou protège le
Worker reçoit l'adresse IP source et les paramètres de la requête. MeteoCompare
n'utilise pas volontairement ces informations pour profiler, suivre ou identifier
les utilisateurs.

Les villes hors France ne déclenchent aucun appel Vigilance. Lorsqu'une ville est
supprimée des favoris, son état Vigilance en mémoire est supprimé et le cache
persistant correspondant est purgé conformément au fonctionnement de l'application.

## 4. Stockage local sur l'appareil

Les données suivantes sont conservées localement par MeteoCompare :

| Donnée | Stockage local | Utilité | Transmission réseau |
|---|---|---|---|
| Villes favorites et leurs coordonnées | DataStore Preferences | Retrouver la sélection de lieux | Les coordonnées du lieu sont envoyées à Open-Meteo lorsqu'une prévision est chargée |
| Modèles météo activés | DataStore Preferences | Respecter la configuration | Les modèles demandés font partie des paramètres des requêtes météo |
| Thème, fréquence de rafraîchissement, affichage du détail et autres préférences | DataStore Preferences | Personnaliser l'application | Non, sauf lorsqu'une préférence modifie directement les paramètres d'une requête météo |
| Langue de l'application | SharedPreferences interne | Choisir la langue de l'interface et des recherches | Le code de langue peut être envoyé au géocodage pour localiser les résultats |
| Cache de prévisions et historiques de calcul | Room SQLite | Démarrage rapide, mode hors-ligne, comparaisons | Le contenu du cache n'est pas téléversé comme tel |
| Cache Vigilance | DataStore Preferences | Limiter les appels réseau et fournir un fallback court | Non comme cache ; de nouvelles requêtes peuvent être faites au Worker |
| Préférences des widgets | DataStore Glance | Conserver ville, couleurs et options du widget | La ville configurée peut déclencher les mêmes requêtes météo qu'à l'intérieur de l'app |

La phrase « stocké localement » signifie que MeteoCompare ne synchronise pas ces
bases ou fichiers vers un serveur MeteoCompare. Elle ne signifie pas que toutes
les informations qu'ils contiennent ne sont jamais utilisées dans une requête
réseau : les coordonnées d'un lieu favori doivent par exemple être envoyées au
service météo pour obtenir ses prévisions.

### Sauvegarde Android

La sauvegarde automatique Android est activée de manière **sélective**. Les règles
de sauvegarde de MeteoCompare autorisent uniquement :

- les préférences générales de l'application ;
- la liste des villes favorites ;
- la préférence de langue.

Ces éléments peuvent être sauvegardés ou transférés par Android vers le compte ou
l'appareil de l'utilisateur selon ses réglages système. MeteoCompare n'a pas accès
à ces sauvegardes.

Les bases Room de cache météo et les autres fichiers non explicitement inclus dans
les règles de sauvegarde ne font pas partie de cette sauvegarde applicative
sélective.

La désinstallation supprime les données locales de l'application présentes sur
l'appareil. Une sauvegarde Android déjà créée reste gérée par Android et par les
réglages du compte de l'utilisateur.

## 5. Permissions Android

| Permission | Utilité |
|---|---|
| `INTERNET` | Appels HTTPS vers Open-Meteo et le Worker MeteoCompare |
| `ACCESS_NETWORK_STATE` | Détecter l'absence de réseau et adapter l'interface / les requêtes |
| `RECEIVE_BOOT_COMPLETED` | Reprogrammer proprement l'actualisation périodique des widgets après un redémarrage ou une mise à jour de l'application |

MeteoCompare **ne demande pas** :

- la localisation GPS ou réseau (`ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`) ;
- l'accès aux contacts, photos ou fichiers externes ;
- l'accès au téléphone ou aux SMS ;
- Bluetooth ou NFC ;
- l'identifiant publicitaire.

La ville consultée est donc une ville **choisie par l'utilisateur** ; l'application
ne lit pas la position physique de l'appareil.

## 6. Sécurité des transmissions

Les services réseau configurés par MeteoCompare utilisent HTTPS. Les données qui
quittent l'appareil sont donc chiffrées en transit entre l'application et les
serveurs contactés selon les mécanismes TLS standards de la plateforme Android.

## 7. Public cible

L'application n'est pas spécifiquement destinée aux enfants de moins de 13 ans.
Elle ne crée aucun compte, profil publicitaire ou historique serveur MeteoCompare
associé à une identité utilisateur.

## 8. Modifications de cette politique

Si une mise à jour ajoute une nouvelle collecte, un système de compte, une
synchronisation cloud, de l'analytics, de la publicité ou une autre transmission
de données significative, cette politique sera mise à jour avant la publication
de la version concernée.

L'engagement de la branche 1.x reste l'absence d'analytics, de publicité, de
tracking et de profilage par MeteoCompare.

## 9. Contact

Pour toute question sur cette politique :
[github.com/Pat0chat/MeteoCompare/issues](https://github.com/Pat0chat/MeteoCompare/issues)
