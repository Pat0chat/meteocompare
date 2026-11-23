# Corpus de référence — conversions et affichage des unités (v1)

Cette suite suit le même principe que les corpus météo : des attentes **versionnées,
reproductibles et indépendantes de `WeatherUnits`**, confrontées aux véritables
fonctions Kotlin exécutées par JUnit sur les configurations **Debug et Release**.
Le corpus est généré hors ligne ; il ne dépend pas de l'heure, de l'API météo, des
paramètres régionaux du poste ni d'Android.

## Périmètre

| Fichier | Références | Contrat contrôlé |
|---|---:|---|
| `profiles.tsv` | 131 | Metric, Imperial, UK et toutes les **128 combinaisons** Custom (2 températures × 4 vents × 2 pluies × 2 pressions × 2 hauteurs × 2 distances) |
| `conversions.tsv` | 198 072 | Valeurs numériques attendues, écarts de température sans décalage de 32, symboles, dix types de valeurs, valeurs signées, bornes et constantes |
| `display.tsv` | 452 736 | Produit cartésien des 131 profils, dix types, six locales, quatre précisions et douze valeurs caractéristiques ; température absolue et différentielle |
| `missing.tsv` | 5 240 | Null, NaN, infini positif et négatif pour chaque profil et grandeur |
| **Total** | **656 179** | Références vérifiées contre le code de production |

Grandeurs : `TEMPERATURE`, `TEMPERATURE_COMPACT`, `WIND_SPEED`,
`PRECIPITATION`, `PRECIPITATION_RATE`, `HEIGHT`, `DISTANCE`, `PRESSURE`,
`PERCENT`, `SECONDS`. Le vent inclut km/h, mph, kn et m/s. Les formats
contrôlés couvrent `en_US`, `fr_FR`, `de_DE`, `es_ES`, `it_IT` et `tr_TR`, ainsi
que les précisions 0, 1, 2 et 6 chiffres. Les cas de précipitation inférieure
à l'unité de résolution doivent afficher une **trace** (`<0.01 in`, etc.) au
lieu d'être assimilés à zéro. Les degrés compacts, les signes, les espaces,
les séparateurs décimaux et les unités doivent correspondre exactement.

Un test supplémentaire vérifie que les 128 combinaisons Custom survivent au
cycle `CustomUnitPreferenceCodec.write()` / `read()`. D'autres assertions
métamorphiques contrôlent la monotonie, l'annulation de l'offset Fahrenheit
sur les écarts et les graduations d'axes.

## Oracle indépendant

`scripts/generate_unit_conversion_corpus.py` définit ses **propres** facteurs
(`1.609344`, `1.852`, `3.6`, `25.4`, `0.3048`, `33.86389`) et la relation
`°F = 9 × °C / 5 + 32`. Il utilise le type `Decimal`, sans importer le code
Kotlin ni recopier le résultat de `WeatherUnits`. Les nombres attendus sont
comparés en Kotlin avec une tolérance limitée aux erreurs IEEE-754 ; les
**chaînes d'affichage** doivent être identiques, sans tolérance.

Le fichier `manifest.json` verrouille les nombres de lignes et les empreintes
SHA-256 de chaque fixture. Modifier un facteur, une locale, un libellé ou la
règle d'arrondi implique une revue explicite du changement d'oracle et de ses
fichiers générés. Les numéros de scénario identifient la combinaison exacte.

## Exécution et preuve CI

Générer/contrôler les références :

```bash
python3 scripts/generate_unit_conversion_corpus.py --check
python3 -m unittest scripts.tests.test_unit_conversion_corpus -v
```

Exécuter le code Android/JVM en Debug **et** en Release :

```bash
./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest
python3 scripts/report_test_results.py --require-corpus both
```

La classe `UnitConversionCorpusTest.kt` est le seul consommateur des quatre
fixtures, et émet une preuve `METEOCOMPARE_UNIT_CORPUS_V1` **après** toutes les
assertions du fichier concerné. Le rapport ne valide un ensemble que si :
(1) le test JUnit a réussi, (2) une seule preuve figure dans le XML, (3) le
nombre exact de cas correspond au manifeste. Une exécution absente, ignorée,
échouée ou un reçu en double reste **non prouvé**. Les rapports Debug et Release
sont distincts. Les 656 179 références ne sont pas 656 179 méthodes JUnit.

**Liaison vérifiée au moteur réel :** le consommateur JVM instancie
`WeatherUnits` à partir des 131 profils, appelle directement
`convert(metric, unit, delta)` pour les 198 072 références numériques,
`value(...)` et `format(...)` pour les 452 736 rendus localisés, puis ces
mêmes méthodes pour les 5 240 cas absents/non finis. Les symboles sont
vérifiés par `label(unit)`. Les valeurs attendues ne proviennent pas de
`WeatherUnits`, mais du générateur Python indépendant. Le test Python
`test_corpus_is_wired_to_real_jvm_execution_not_only_python_checks` verrouille
les points d'entrée du consommateur JVM ; **seul** le passage des tests
`UnitConversionCorpusTest` dans Gradle prouve que les assertions ont exécuté
le code de production. Les vues Compose et la persistance DataStore sont
couvertes séparément, pas par les 656 179 vecteurs.

**Limites :** cette suite protège le cœur de conversion/formatage. Elle ne
remplace pas les tests instrumentés pour la taille réelle des libellés Compose,
les ressources Android localisées, les widgets ou le parcours des paramètres.
