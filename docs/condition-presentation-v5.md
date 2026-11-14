# MeteoCompare — présentation des conditions V5

## Objectifs et périmètre

Ce changement concerne **uniquement l'interface et le nettoyage de code**. Il n'altère ni `ConditionDecisionEngine`, ni le consensus ou les seuils météorologiques de V4. Le versionName de l'application reste `1.22.1` tant que la release Android n'est pas officiellement préparée.

## Parcours utilisateur

- **Home** : plus de légende globale dans les cartes météo. L'icône courante et les icônes de la mini-prévision conservent leur diagnostic consultable par appui.
- **Chronologie par heures et Daily** : le point d'entrée « Comment lire les conditions météo ? » se trouve après les sélecteurs de période et de présentation, juste avant les prévisions (même place pour la vue en colonnes et la vue chrono).
- **Chart View** : l'aide reste dans le panneau d'introduction.
- **Tableaux par modèle** : la légende d'état reste à côté de la légende météo habituelle.
- **Comparaison des moteurs** : l'aide est désormais hors du conteneur à défilement horizontal, et reste donc visible sur les écrans étroits.
- **Widgets/notifications** : aucun changement de protocole ou de rendu.

## Dialogue Material 3

`ConditionInfoDialog` centralise le cadre (coins arrondis, largeur contrainte, hauteur adaptable, bouton de fermeture toujours accessible, contenu défilant). Deux contenus indépendants réutilisent cette structure :

- `ConditionExplanationDialog` : grande icône de la prévision, statut, description pédagogique, alternatives illustrées le cas échéant, méthode de calcul, barres de couverture et d'accord, motifs et phénomènes notables. Un score d'accord **n'est pas** une probabilité de vérification.
- `ConditionStatusLegend` : cinq statuts, leurs symboles et leurs descriptions, dans une liste de cartes thématiques.

Le rendu utilise `MaterialTheme.colorScheme`, ce qui suit les modes clair/sombre et les couleurs de l'application. Les six catalogues (`fr`, `en`, `de`, `es`, `it`, `tr`) ont des textes propres aux nouveaux champs ; les anciennes explications laissées en anglais dans plusieurs catalogues ont été traduites.

## Organisation du code et nettoyage

- `ConditionStatusIcon.kt` : icône compacte, marqueurs et mappage des libellés.
- `ConditionExplanationDialog.kt` : fiche de détail, provenance et indicateurs.
- `ConditionStatusLegend.kt` : entrée d'aide et liste pédagogique.
- `ConditionInfoDialog.kt` : conteneur Material 3 réutilisé par les deux fenêtres.
- `ConditionPresentationV5Previews.kt` (debug uniquement) : prévisualisations de l'icône, des dialogues et de la légende.
- Suppression de chaînes XML de diagnostic devenues orphelines ; aucune clé XML inutilisée restante selon `check_android_strings.py`.

Les tests de raccordement V3/V4 ont été actualisés pour refléter le nouveau placement et la séparation des composants. Les tests Compose ont été enrichis (fenêtre défilante, fermeture, indisponibilité) et les tests Python V5 vérifient la présence/absence de la légende et l'architecture du composant. Aucun chemin du moteur de calcul des conditions n'a été modifié.

## Tests possibles sans Android/Gradle

```bash
python -m unittest discover -s scripts/tests -p 'test_*.py' -q
python scripts/validate-compose-previews.py
python scripts/check_android_strings.py
git diff --check
```

### Vérifications Android restant à effectuer

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug
```

Contrôler visuellement en thèmes clair et sombre, sur petits écrans et paysages, et en français/anglais : Home (sans légende), chronologie (légende au-dessus des données), Card/Chart View, comparaison des moteurs (panneau fixe au-dessus de la table horizontale) et popups pour chaque statut. Les contrôles statiques ne remplacent ni la compilation Compose ni les tests instrumentés sur appareil.
