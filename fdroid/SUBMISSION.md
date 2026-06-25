# Soumission à F-Droid

F-Droid est le store FOSS d'Android. Il rebuilde toutes les apps depuis le
code source et vérifie l'absence de tracking propriétaire. La soumission
est un processus de PR sur leur repo `fdroiddata`.

## Pré-requis (déjà satisfaits par ce projet)

- ✅ **Code 100 % FOSS** — toutes les dépendances sont Apache 2.0 / MIT
- ✅ **LICENSE** présente à la racine (Apache 2.0)
- ✅ **Aucun tracker propriétaire** — pas de Firebase, Crashlytics, GMS Ads…
- ✅ **Build reproductible** — Gradle standard avec versions verrouillées
  dans `gradle/libs.versions.toml`
- ✅ **Tag de release Git** — F-Droid build depuis `v0.1.0`, pas de
  branches mobiles
- ✅ **Pas d'anti-features** — l'app ne dépend que d'Open-Meteo qui est
  lui-même FOSS, donc pas de flag `NonFreeNet`

## Procédure de soumission

### 1. Fork le repo fdroiddata

```bash
# Sur GitLab.com (pas GitHub — F-Droid est hébergé sur GitLab)
# Fork : https://gitlab.com/fdroid/fdroiddata
git clone https://gitlab.com/TON_USER/fdroiddata.git
cd fdroiddata
```

### 2. Copie le metadata

```bash
cp /chemin/vers/MeteoCompare/fdroid/com.meteocompare.app.yml \
   metadata/com.meteocompare.app.yml
```

**Avant de commit, vérifie** que tous les `USERNAME` ont été remplacés par
ton vrai handle GitHub.

### 3. Test local (optionnel mais recommandé)

```bash
# Installer fdroidserver
pip install fdroidserver

# Lint le metadata
fdroid lint com.meteocompare.app

# Build test (peut prendre du temps — il rebuild from scratch)
fdroid build --verbose --on-server --no-tarball com.meteocompare.app
```

### 4. Push la PR

```bash
git checkout -b add-meteocompare
git add metadata/com.meteocompare.app.yml
git commit -m "New app: MeteoCompare"
git push origin add-meteocompare
```

Ouvrir une Merge Request sur GitLab pointant vers `master` de fdroiddata.

### 5. Review

Le bot F-Droid (`fdroid-buildserverbot`) tente automatiquement de builder
l'app. Si ça passe, un humain review le metadata (~ quelques jours à
quelques semaines selon la charge). Les retours sont publiés en commentaire
de la MR.

Une fois mergé, l'app apparaît sur f-droid.org dans les ~24h suivantes.

### 6. Mises à jour suivantes

Avec `UpdateCheckMode: Tags` + `AutoUpdateMode: Version` dans le metadata,
chaque nouveau tag `vX.Y.Z` sur GitHub déclenchera automatiquement un
build F-Droid sans nouvelle MR. C'est le principal avantage de bien
configurer ces deux champs.

## Anti-features à connaître

F-Droid documente les "anti-features" qu'une app peut avoir. Pour
MeteoCompare, **aucune** ne s'applique :

| Anti-feature       | S'applique ?  | Pourquoi                                   |
|--------------------|---------------|--------------------------------------------|
| `Ads`              | ❌            | Aucune publicité                           |
| `Tracking`         | ❌            | Aucun analytics ni télémétrie              |
| `NonFreeNet`       | ❌            | Open-Meteo est FOSS                        |
| `NonFreeDep`       | ❌            | Toutes les libs Apache/MIT                 |
| `NonFreeAdd`       | ❌            | Pas d'addons propriétaires                 |
| `UpstreamNonFree`  | ❌            | Pas applicable                             |
| `NonFreeAssets`    | ❌            | Toutes les ressources libres               |
| `KnownVuln`        | ❌            | Vérifié                                    |

C'est rare et c'est un signal de qualité fort pour F-Droid.

## Badge F-Droid pour le README

Une fois l'app publiée :

```markdown
[![F-Droid](https://img.shields.io/f-droid/v/com.meteocompare.app)](https://f-droid.org/packages/com.meteocompare.app/)
```

Et le bouton "Get it on F-Droid" pour le README :

```markdown
[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="80">](https://f-droid.org/packages/com.meteocompare.app/)
```

## Ressources

- Doc officielle : https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/
- Format metadata : https://f-droid.org/docs/Build_Metadata_Reference/
- Inclusion policy : https://f-droid.org/docs/Inclusion_Policy/
