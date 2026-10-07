# Google Play — fiche « Sécurité des données »

*Référence de préparation pour MeteoCompare 1.17.0 — 7 octobre 2026.*

Ce fichier est une **checklist de déclaration**, pas une exportation automatique de
la Play Console. La déclaration finale doit être revue dans la Console au moment de
chaque release, car Google et les services tiers peuvent faire évoluer leurs règles.

## Obligation de déclaration

Le formulaire « Sécurité des données » est obligatoire pour toute application publiée
sur une piste fermée, ouverte ou en production, même si l'application ne collecte
aucune donnée utilisateur au sens affiché par Google Play. Une URL publique de la
politique de confidentialité est également requise.

Référence officielle :
`https://support.google.com/googleplay/android-developer/answer/10787469`

## Inventaire MeteoCompare 1.17

MeteoCompare n'intègre aucun SDK publicitaire, analytics ou crash reporting, ne crée
aucun compte et ne demande aucune permission Android de localisation.

Les transmissions fonctionnelles à vérifier dans le formulaire sont :

| Flux | Données envoyées | Destinataire | Finalité |
|---|---|---|---|
| Recherche de lieu | texte recherché, langue | Open-Meteo Geocoding | Fonctionnement de l'application |
| Prévisions / historique / marine | coordonnées du **lieu choisi**, modèles et paramètres météo | Open-Meteo | Fonctionnement de l'application |
| Vigilance FR | code département, indicateur côte | Worker MeteoCompare | Fonctionnement de l'application |
| Radar pluie | coordonnées du **lieu choisi** | RainViewer | Fonctionnement de l'application |
| Fond de carte radar | indices de tuiles correspondant à la zone affichée | OpenStreetMap | Fonctionnement de l'application |

Comme pour toute connexion Internet, les serveurs contactés reçoivent aussi l'adresse
IP source et des métadonnées HTTP nécessaires au transport.

## Points de décision dans la Play Console

1. **Ne pas déclarer une permission de localisation** : l'application ne lit pas la
   position physique de l'appareil et ne demande ni `ACCESS_COARSE_LOCATION` ni
   `ACCESS_FINE_LOCATION`.
2. Une ville choisie manuellement n'est pas automatiquement la position physique de
   l'utilisateur. En revanche, si un fournisseur utilise l'adresse IP pour **inférer
   la position**, Google demande de déclarer la catégorie de position correspondante.
3. Google définit « collecter » comme le fait de transmettre des données hors de
   l'appareil, y compris vers du code ou des services tiers. Les traitements purement
   éphémères disposent d'un traitement spécifique dans le formulaire, mais doivent
   être évalués selon la pratique réelle du fournisseur. Ne pas qualifier par défaut
   les appels Open-Meteo d'« éphémères » tant que leur journalisation côté serveur
   (décrite dans `PRIVACY.md`) n'a pas été revalidée pour la release.
4. Vérifier avant soumission les politiques actuelles d'Open-Meteo, RainViewer,
   OpenStreetMap et de l'hébergeur du Worker, notamment leur journalisation d'IP et
   de paramètres de requête. La politique `PRIVACY.md` doit rester cohérente avec la
   réponse donnée dans la Play Console.
5. **Chiffrement en transit : Oui** pour les flux gérés par MeteoCompare : les URL
   configurées dans l'application utilisent HTTPS.
6. **Publicité : Non** ; **analytics : Non** ; **compte utilisateur : Non** ;
   **identifiant publicitaire : Non**.

## Gate release

Avant passage en production :

- [ ] formulaire « Sécurité des données » ouvert et revu dans Play Console ;
- [ ] URL publique de `PRIVACY.md` renseignée ;
- [ ] pratiques des quatre fournisseurs réseau revérifiées ;
- [ ] réponses de la Console comparées à ce document et à `PRIVACY.md` ;
- [ ] nouvelle transmission réseau introduite par la release ajoutée à cet inventaire.
