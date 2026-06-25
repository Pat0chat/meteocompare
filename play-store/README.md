# Play Store assets

Tout ce qu'il faut pour soumettre l'app à Play Console.

## Structure

```
play-store/
├── assets/
│   ├── icon-512.png                  ← Icône hi-res Play Store (obligatoire)
│   ├── feature-graphic.png           ← Bandeau 1024×500 (obligatoire)
│   └── icon-monochrome-preview.png   ← Aperçu monochrome themed (info)
├── descriptions/
│   ├── short-fr.txt                  ← Description courte FR (≤80 car.)
│   ├── short-en.txt                  ← Description courte EN
│   ├── full-fr.txt                   ← Description complète FR
│   └── full-en.txt                   ← Description complète EN
└── screenshots-placeholders/         ← À remplir manuellement (voir ci-dessous)
```

## Screenshots — à capturer manuellement

Play Store exige minimum 2 screenshots par form factor. Recommandations :

### Téléphone (obligatoire — au moins 2)
- Format JPG ou PNG 24 bits
- Ratio entre 16:9 et 9:16
- Côté le plus long ≤ 3840 px, côté le plus court ≥ 320 px

### Captures à faire dans l'app

1. **Écran d'accueil** avec 3-4 villes en favoris, valeurs chargées avec des
   confidences variées (vert / orange / rouge) pour montrer l'indice.
2. **Détail d'une ville — résumé jour** : montrer la TodaySummaryCard avec
   toutes les valeurs (T max, T min, pluie, vent) et les badges de confiance.
3. **Détail d'une ville — bande de confiance** : c'est LE shot signature.
   Choisir une ville où on voit la bande s'élargir nettement.
4. **Détail d'une ville — comparaison des modèles** : les courbes superposées.
5. **Écran Settings** : la liste des modèles avec checkboxes — montre la
   transparence sur les sources de données.

### Mode opératoire

Avec un device branché en USB en mode debug :

```bash
# Liste les devices
adb devices

# Capture
adb shell screencap /sdcard/screenshot1.png
adb pull /sdcard/screenshot1.png ./play-store/screenshots-placeholders/01-home.png
```

Ou via Android Studio : *View → Tool Windows → Logcat → Screenshot icon*.

## Checklist Play Console

Au moment du soumission :

- [ ] APK release signé uploadé (issu de `./gradlew :app:bundleRelease`, AAB préféré)
- [ ] Catégorie : Météo
- [ ] Contenu : Tous publics
- [ ] Politique de confidentialité : URL pointant vers `PRIVACY.md` (héberger sur GitHub Pages par exemple)
- [ ] Section "Data Safety" : déclarer "Aucune donnée collectée, aucune donnée partagée"
  - Lors du formulaire : tout cocher "Non" sauf les questions sur le stockage local
- [ ] Permissions sensibles : aucune (l'app n'utilise ni GPS, ni contacts, ni stockage externe)
- [ ] Annonces : Non (aucune publicité)
- [ ] Contenu UGC : Non (pas de contenu utilisateur)
- [ ] Coffre-fort des données : Non requis
- [ ] Test interne avant production : recommandé (test track avec 1-3 testeurs)

## Hébergement de la politique de confidentialité

Play Console exige une URL publique. Options :

1. **GitHub Pages** (gratuit) — activer Pages sur le repo, la PRIVACY.md
   apparaît à `https://USERNAME.github.io/MeteoCompare/PRIVACY.html` après
   conversion automatique.
2. **Gist** : créer un gist public avec le contenu de PRIVACY.md → URL "raw".

## Conformité Open-Meteo

L'usage gratuit d'Open-Meteo est limité à 10 000 requêtes / jour pour usage
non commercial. Le cache local de l'app divise drastiquement le nombre de
requêtes par utilisateur (typiquement 5-10 par jour par utilisateur actif).

Si l'app devient un succès et dépasse les limites de l'offre gratuite,
souscrire à l'offre commerciale Open-Meteo (à partir de 29 €/mois) ou
héberger soi-même via leur image Docker open-source.
