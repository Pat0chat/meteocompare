# Politique de confidentialité — MeteoCompare

*Dernière mise à jour : juin 2026*

## TL;DR

**MeteoCompare ne collecte aucune donnée personnelle.** Aucun analytics, aucun
tracking, aucune publicité, aucun crash reporting, aucune télémétrie. Toutes
les données restent sur votre appareil.

---

## 1. Données que nous collectons

**Aucune.** L'application MeteoCompare ne dispose d'aucun serveur lui appartenant
et ne transmet aucune donnée à aucune tierce partie à des fins de profilage,
analyse, publicité ou tracking.

Concrètement, cela signifie :

- ❌ Pas de Google Analytics, Firebase, ou autre SDK d'analytics
- ❌ Pas de Crashlytics ou autre crash reporting
- ❌ Pas d'Advertising ID — la permission `AD_ID` est explicitement retirée du manifest
- ❌ Pas de réseaux publicitaires (AdMob, etc.)
- ❌ Pas de cookies (web ou natifs)
- ❌ Pas de fingerprinting de l'appareil
- ❌ Pas de tracking inter-applications

## 2. Données que nous partageons

**Aucune.** Voir section 1.

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

## 4. Stockage local sur votre appareil

L'application stocke les données suivantes **uniquement** sur votre appareil :

| Donnée                 | Stockage             | Pourquoi                                      |
|------------------------|----------------------|-----------------------------------------------|
| Villes favorites       | DataStore (interne)  | Pour vous afficher votre sélection            |
| Modèles activés        | DataStore (interne)  | Pour respecter votre configuration            |
| Cache des prévisions   | Room SQLite (interne) | Pour démarrage instantané et mode offline    |

Ces données :
- Ne quittent **jamais** votre appareil
- Sont supprimées si vous désinstallez l'application
- Peuvent être incluses dans la sauvegarde automatique Android vers votre
  **propre** compte Google (sous votre contrôle dans Réglages → Système →
  Sauvegarde). Nous n'avons aucun accès à ces sauvegardes.

## 5. Permissions Android demandées

| Permission             | Pourquoi                                          |
|------------------------|---------------------------------------------------|
| `INTERNET`             | Requêtes vers Open-Meteo (seul backend utilisé)  |
| `ACCESS_NETWORK_STATE` | Détecter mode hors-ligne pour bandeau informatif |

L'application **ne demande pas** :
- Localisation (GPS / réseau)
- Accès aux contacts, photos, fichiers
- Téléphone, SMS
- Bluetooth, NFC
- Identifiant publicitaire (`AD_ID` explicitement retiré)

## 6. Public cible

L'application n'est pas spécifiquement destinée aux enfants de moins de 13 ans.
Aucune donnée n'étant collectée, l'application est conforme par défaut au COPPA
et au RGPD pour cette tranche d'âge.

## 7. Modifications de cette politique

Si une mise à jour de l'application change fondamentalement ce comportement
(par exemple ajout d'un système d'authentification ou de synchronisation cloud),
cette politique sera mise à jour avant la publication de la version concernée,
et l'utilisateur sera explicitement informé dans les notes de version.

Tant que la version reste 0.x, l'engagement "zéro collecte" est maintenu.

## 8. Contact

Pour toute question sur cette politique :
[github.com/USERNAME/MeteoCompare/issues](https://github.com/USERNAME/MeteoCompare/issues)
