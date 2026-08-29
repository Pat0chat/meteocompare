# Politique de confidentialité — MeteoCompare

*Dernière mise à jour : août 2026*

## TL;DR

**MeteoCompare n'utilise aucune donnée personnelle à des fins de profilage.** Aucun
analytics, tracking, publicité, crash reporting ou télémétrie applicative. Les
favoris, préférences, caches météo et historiques restent sur votre appareil.

---

## 1. Données que nous collectons

MeteoCompare ne constitue aucun fichier d'utilisateurs et ne transmet aucune donnée
à des fins de profilage, analyse, publicité ou tracking. Les requêtes réseau
nécessaires aux fonctions météo sont décrites ci-dessous.

Concrètement, cela signifie :

- ❌ Pas de Google Analytics, Firebase, ou autre SDK d'analytics
- ❌ Pas de Crashlytics ou autre crash reporting
- ❌ Pas d'Advertising ID : aucune dépendance de MeteoCompare ne déclare ou n'injecte la permission AD_ID dans le manifeste de l'application.
- ❌ Pas de réseaux publicitaires (AdMob, etc.)
- ❌ Pas de cookies (web ou natifs)
- ❌ Pas de fingerprinting de l'appareil
- ❌ Pas de tracking inter-applications

## 2. Données que nous partageons

MeteoCompare ne partage aucune donnée à des fins publicitaires, analytiques ou de profilage.

Pour fournir ses fonctionnalités météo, l'application transmet toutefois les paramètres nécessaires à Open-Meteo et, pour la Vigilance officielle française, au Worker public MeteoCompare décrit ci-dessous.

## 3. Utilisation d'Open-Meteo (service tiers)

L'application interroge l'API publique d'[Open-Meteo](https://open-meteo.com)
pour récupérer les prévisions météorologiques.

**Quand l'application appelle Open-Meteo :**
- Lors d'une recherche de ville (API geocoding) → la chaîne tapée est envoyée
- Lors du chargement d'une ville favorite → les coordonnées (latitude, longitude)
  de la ville sont envoyées
- Lors d'un pull-to-refresh ou d'un changement de modèle

**Ce qu'Open-Meteo voit :**
- Votre adresse IP (intrinsèque à toute requête HTTP)
- La requête HTTP (paramètres : ville recherchée OU coordonnées)
- L'agent utilisateur HTTP standard d'Android

**Ce qu'Open-Meteo NE voit PAS :**
- Votre identité, votre compte Google, votre identifiant Android
- L'historique de vos précédentes requêtes (chaque requête est anonyme)
- Vos villes favorites ou préférences (stockées uniquement sur votre appareil)

Open-Meteo opère selon sa propre [politique de confidentialité](https://open-meteo.com/en/terms#privacy)
qui spécifie l'absence de stockage à long terme des requêtes individuelles.

## 4. Vigilance Météo-France via le Worker MeteoCompare

Pour les villes françaises, l'application peut interroger le Worker public
`https://meteocompare.app/_mcx/vigilance` afin d'afficher la Vigilance officielle
Météo-France (jaune, orange, rouge). L'application envoie uniquement :

- le **code du département** (par exemple `91`) ;
- `coast=1` lorsque la localité est identifiée comme côtière (ou que le mode Mer / côte est activé), afin d'inclure la vigilance littorale.

Le Worker gère côté serveur l'authentification Météo-France et le cache. **Aucun
identifiant, mot de passe ou token Météo-France n'est stocké dans l'application.**
Comme pour toute requête HTTPS, l'infrastructure réseau/Cloudflare voit l'adresse
IP source et les paramètres de la requête. MeteoCompare n'utilise pas ces données
pour profiler, suivre ou identifier l'utilisateur.

Les villes hors France ne déclenchent aucun appel Vigilance. Lorsqu’une ville est supprimée des favoris, son état Vigilance en mémoire est supprimé immédiatement et le cache persistant du département concerné est purgé.

## 5. Stockage local sur votre appareil

L'application stocke les données suivantes **uniquement** sur votre appareil :

| Donnée               | Stockage             | Pourquoi                                  |
|----------------------|----------------------|-------------------------------------------|
| Villes favorites     | DataStore (interne)  | Pour vous afficher votre sélection        |
| Modèles activés      | DataStore (interne)  | Pour respecter votre configuration        |
| Cache des prévisions | Room SQLite (interne) | Pour démarrage instantané et mode offline |
| Cache Vigilance      | DataStore (interne)  | Limiter les appels réseau et permettre un fallback court |
| Préférence langage   | Room SQLite (interne) | Langue de l'application                   |
| Préférence thème     | Room SQLite (interne) | Thème de l'application                    |

Ces données :
- Ne quittent **jamais** votre appareil
- Sont supprimées si vous désinstallez l'application
- Peuvent être incluses dans la sauvegarde automatique Android vers votre
  **propre** compte Google (sous votre contrôle dans Réglages → Système →
  Sauvegarde). Nous n'avons aucun accès à ces sauvegardes.

## 6. Permissions Android demandées

| Permission             | Pourquoi                                          |
|------------------------|---------------------------------------------------|
| `INTERNET`             | Requêtes vers Open-Meteo et le Worker MeteoCompare |
| `ACCESS_NETWORK_STATE` | Détecter mode hors-ligne pour bandeau informatif |

L'application **ne demande pas** :
- Localisation (GPS / réseau)
- Accès aux contacts, photos, fichiers
- Téléphone, SMS
- Bluetooth, NFC
- Identifiant publicitaire (`AD_ID` explicitement retiré)

## 7. Public cible

L'application n'est pas spécifiquement destinée aux enfants de moins de 13 ans.
L'application ne crée aucun compte, profil publicitaire ou historique serveur associé à un utilisateur.

## 8. Modifications de cette politique

Si une mise à jour de l'application change fondamentalement ce comportement
(par exemple ajout d'un système d'authentification ou de synchronisation cloud),
cette politique sera mise à jour avant la publication de la version concernée,
et l'utilisateur sera explicitement informé dans les notes de version.

Tant que la version reste 1.x, l’engagement sans analytics, tracking, publicité ni profilage est maintenu.

## 9. Contact

Pour toute question sur cette politique :
[github.com/Pat0chat/MeteoCompare/issues](https://github.com/Pat0chat/MeteoCompare/issues)
