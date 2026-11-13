# GitHub Actions — rapport de tests

Les workflows **Quality checks** et **Build & release** publient un rapport consolidé
à l'onglet **Actions → exécution → job « Consolidated test report » → Summary**.
Un rapport JSON et une version Markdown sont téléchargeables dans les artefacts :
`quality-consolidated-test-report` et `release-consolidated-test-report`.

## Ce qui est compté

- **Unitaires Debug** : méthodes exécutées dans `:app:testDebugUnitTest`, via les XML JUnit Gradle.
- **Unitaires Release** : exécutions distinctes de `:app:testReleaseUnitTest` ; les méthodes
  communes aux deux variantes sont comptées deux fois *en tant qu'exécutions*, pas comme
  deux tests différents.
- **AndroidTests instrumentés** : cas réellement exécutés sur l'émulateur, depuis les XML
  de `:app:connectedDebugAndroidTest`.
- **Corpus** : 74 537 **lignes/scénarios de test**, contenues dans neuf fichiers TSV.
  Ce ne sont **pas** 74 537 méthodes JUnit. Une famille est « validée » uniquement si
  la méthode de test Kotlin qui a parcouru son fichier TSV a réussi **et produit un reçu avec le nombre exact de lignes**. Le rapport donne
  ses totaux **pour Debug et pour Release séparément**. Un échec de méthode signifie
  « scénarios affectés », et non que toutes les lignes de ce fichier sont erronées.
- **Python** : comptes éventuellement extraits du journal `unittest` de la CI qualité.

**Preuve de lecture :** la sortie JUnit XML doit contenir `METEOCOMPARE_CORPUS_V1 file=... cases=... elapsed_ms=...`, émis après le passage des assertions sur chaque ligne. Sans reçu, avec un nombre différent, un reçu dupliqué ou une méthode absente, la famille est « non vérifiée ». La CI exige `--require-corpus both` dans les jobs JVM. Les tâches JVM de test ne sont pas récupérées du cache.

**Aucune donnée absente n'est convertie en succès** : si Gradle, l'émulateur ou un
job s'interrompt avant l'émission des XML, la suite est marquée « non exécutée ».
Les résultats XML invalides sont signalés. La réussite du workflow reste déterminée
par Gradle / les assertions et non par le script de rapport (qui peut tourner après
l'échec d'un job).

## Où trouver les détails d'un échec

Les fichiers XML bruts sont conservés dans `quality-unit-test-results`,
`androidTest-reports`, `release-unit-test-results` et
`release-androidTest-reports`. Les rapports HTML instrumentés sont également dans
l'artefact Android. Le rapport résume les premières erreurs avec nom de classe et test.

## Générer le rapport localement

Après l'exécution des tests sur Android :

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:testReleaseUnitTest :app:connectedDebugAndroidTest
python scripts/report_test_results.py --markdown test-summary.md --json test-summary.json
```

Depuis GitHub Actions, `--summary "$GITHUB_STEP_SUMMARY"` affiche ce même récapitulatif
sans perturber l'état du build.

Pour tester le générateur sans Android :

```bash
python3 -m unittest scripts.tests.test_ci_test_report -v
python3 scripts/generate_weather_regression_corpus.py --check
```

## Maintien du lien entre fixtures et méthodes JUnit

Le dictionnaire `CORPUS_CONSUMERS` de `scripts/report_test_results.py` associe chaque
fichier TSV à la méthode Kotlin qui le charge. Lorsqu'une famille est ajoutée, mettre
simultanément à jour ce dictionnaire, son oracle/générateur, les tests Kotlin et le
manifest `app/src/test/resources/weather_regression/v1/manifest.json`.
Une incohérence de manifest provoque une erreur de génération du rapport au lieu de
présenter un total trompeur.

Voir aussi `docs/WEATHER_CORPUS_COVERAGE_AUDIT_1_22_0.md` pour les limites de couverture et les durées mesurées.
