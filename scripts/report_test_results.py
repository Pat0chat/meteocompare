#!/usr/bin/env python3
"""Summarise Gradle JUnit XML and reviewed regression-corpus scenarios for CI.

No results are interpreted as 'not executed', never as zero failures / success.
Corpus TSV rows are counted only when their *actual* JUnit consumer passed.
The same unit methods run in debug and release are reported as executions.
"""

import argparse
from dataclasses import asdict, dataclass, field
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path

# Keep in sync with the exact files loaded by the production-code JUnit suites.
CORPUS_CONSUMERS = {
    'wmo.tsv': ('WeatherRegressionCorpusTest', 'allWmoCodesMatchReviewedMapping'),
    'clouds.tsv': ('WeatherRegressionCorpusTest', 'exhaustiveCloudLayerAndSolarBoundaryGrid'),
    'daily.tsv': ('WeatherRegressionCorpusTest', 'dailyDominanceUsesPhysicalHoursAcrossZonesAndDst'),
    'consensus.tsv': ('WeatherRegressionCorpusTest', 'independentFamilyConsensusAndDryWetTiesMatchGoldenCases'),
    'sunshine.tsv': ('WeatherRegressionCorpusTest', 'hourlySunshineRespectsExplicitDurationMissingDataAndDaylight'),
    'sunshine_consensus.tsv': ('WeatherRegressionCorpusTest', 'sunshineConsensusRequiresIndependentModelEvidenceUnderOvercastSky'),
    'widget_layout.tsv': ('WidgetRegressionCorpusTest', 'launcherGeometryAndShapeTransitionsMatchGoldenContracts'),
    'widget_metrics.tsv': ('WidgetRegressionCorpusTest', 'responsiveMetricRowsHaveExpectedDensityAndOrder'),
    'widget_refresh.tsv': ('WidgetRegressionCorpusTest', 'refreshTimingAndClockRollbackCannotFreezeTheWidget'),
}


@dataclass
class Suite:
    total: int = 0
    passed: int = 0
    failed: int = 0
    skipped: int = 0
    files: int = 0
    warnings: list[str] = field(default_factory=list)
    failures: list[str] = field(default_factory=list)
    methods: dict[str, str] = field(default_factory=dict)
    # stdout receipts emitted AFTER the exact number of production assertions has passed.
    receipts: dict[str, list[dict]] = field(default_factory=dict)


CORPUS_RECEIPT = re.compile(
    r'(?m)^METEOCOMPARE_CORPUS_V1 file=([a-z_]+\.tsv) cases=(\d+) elapsed_ms=(\d+)\s*$'
)

def scan_junit(path: Path) -> Suite:
    result = Suite()
    if not path.exists():
        return result
    for xml in sorted(path.rglob('*.xml')):
        if not xml.name.startswith('TEST-'):
            continue
        try:
            root = ET.parse(xml).getroot()
        except (OSError, ET.ParseError) as exc:
            result.warnings.append(f'{xml}: {exc}')
            continue
        cases = [node for node in root.iter() if node.tag.rsplit('}', 1)[-1] == 'testcase']
        if not cases:
            continue
        result.files += 1
        # Test stdout is normally attached to <testsuite><system-out> by Gradle;
        # some JUnit exporters attach it to individual <testcase> nodes instead.
        classes_in_xml = {
            (case.attrib.get('classname', '') or root.attrib.get('name', '')).rsplit('.', 1)[-1]
            for case in cases
        }
        for node in root.iter():
            if node.tag.rsplit('}', 1)[-1] != 'system-out':
                continue
            for match in CORPUS_RECEIPT.finditer(node.text or ''):
                file = match.group(1)
                klass = CORPUS_CONSUMERS.get(file, (None, None))[0]
                if klass not in classes_in_xml:
                    result.warnings.append(f'{xml}: unexpected corpus receipt for {file}')
                    continue
                result.receipts.setdefault(file, []).append({
                    'cases': int(match.group(2)), 'elapsed_ms': int(match.group(3))
                })
        for case in cases:
            result.total += 1
            method = case.attrib.get('name', '')
            classname = case.attrib.get('classname', '') or root.attrib.get('name', '')
            key = classname.rsplit('.', 1)[-1] + '.' + method
            children = {n.tag.rsplit('}', 1)[-1] for n in case}
            if 'failure' in children or 'error' in children:
                state = 'failed'
                result.failed += 1
                failure = next(n for n in case if n.tag.rsplit('}', 1)[-1] in ('failure', 'error'))
                message = (failure.attrib.get('message') or (failure.text or '')).splitlines()[0:1]
                result.failures.append(f'{classname}.{method}: {message[0] if message else "failed"}')
            elif 'skipped' in children:
                state = 'skipped'
                result.skipped += 1
            else:
                state = 'passed'
                result.passed += 1
            # Duplicated executions of one method are not a unique pass.
            previous = result.methods.get(key)
            if previous is None or state != 'passed':
                result.methods[key] = state
    return result


def read_corpus_manifest(path: Path) -> dict[str, int]:
    data = json.loads(path.read_text(encoding='utf-8'))
    counts = {key: int(item['cases']) for key, item in data['files'].items()}
    if set(counts) != set(CORPUS_CONSUMERS):
        raise ValueError('Corpus manifest files differ from reviewed JUnit consumer mapping')
    if any(n <= 0 for n in counts.values()):
        raise ValueError('Corpus case count must be positive')
    return counts


def corpus_status(suite: Suite, counts: dict[str, int]):
    passed = 0
    failed = 0
    pending = 0
    for file, number in counts.items():
        klass, method = CORPUS_CONSUMERS[file]
        state = suite.methods.get(f'{klass}.{method}')
        receipts = suite.receipts.get(file, [])
        if state == 'passed' and len(receipts) == 1 and receipts[0]['cases'] == number:
            passed += number
        elif state == 'failed':
            failed += number  # cases affected; not individual failing rows
        else:
            pending += number
    return {'validated': passed, 'affected_by_failing_test': failed,
            'not_verified': pending, 'total': sum(counts.values())}


def python_status(path: Path | None):
    if path is None or not path.exists():
        return None
    log = path.read_text(encoding='utf-8', errors='replace')
    match = re.findall(r'Ran (\d+) tests? in [\d.]+s', log)
    if not match:
        return None
    total = int(match[-1])
    failures = 0
    skipped = 0
    status = re.findall(r'FAILED \(([^)]*)\)', log)
    if status:
        for field in status[-1].split(','):
            name, _, count = field.strip().partition('=')
            if name in ('failures', 'errors'):
                failures += int(count)
            if name == 'skipped':
                skipped += int(count)
    else:
        ok = re.findall(r'^OK(?: \(([^)]*)\))?\s*$', log, re.M)
        if not ok:
            return None
        for field in ok[-1].split(','):
            name, _, count = field.strip().partition('=')
            if name == 'skipped':
                skipped += int(count)
    return {'total': total, 'passed': max(0, total - failures - skipped),
            'failed': failures, 'skipped': skipped}


def md_cell(text):
    return str(text).replace('|', '\\|').replace('\n', ' ')[:260]


def render(suites, corpus, python, counts=None):
    lines = [
        '## MeteoCompare — bilan des tests', '',
        '| Suite | Réussis | Échecs | Ignorés | Total | État |',
        '|---|---:|---:|---:|---:|---|',
    ]
    for label, key in [('Unitaires Debug', 'debug'), ('Unitaires Release', 'release'),
                       ('AndroidTests instrumentés', 'android')]:
        suite = suites[key]
        if suite.warnings or suite.failed:
            status = 'Échec / rapport incomplet'
        elif not suite.files:
            status = 'Non exécuté'
        elif suite.passed == 0:
            status = 'Ignorés'
        else:
            status = 'OK'
        lines.append(f'| {label} | {suite.passed} | {suite.failed} | {suite.skipped} | {suite.total} | {status} |')
    if python:
        lines.append(f'| Tests Python | {python["passed"]} | {python["failed"]} | '
                     f'{python["skipped"]} | {python["total"]} | '
                     f'{"OK" if not python["failed"] else "Échec"} |')
    else:
        lines.append('| Tests Python | — | — | — | — | Non exécuté / journal absent |')
    lines += ['', '**Corpus de non-régression (scénarios de données, et non tests JUnit indépendants)**', '',
              '| Variante | Scénarios validés | Affectés par un test en échec | Non vérifiés | Total |',
              '|---|---:|---:|---:|---:|']
    for label, variant in [('Debug', 'debug'), ('Release', 'release')]:
        entry = corpus[variant]
        lines.append(f'| {label} | {entry["validated"]:,} | '
                     f'{entry["affected_by_failing_test"]:,} | {entry["not_verified"]:,} | '
                     f'{entry["total"]:,} |'.replace(',', ' '))
    lines += ['', '**Preuves d’exécution réelles (extraites des sorties JUnit, après les assertions Kotlin)**', '',
              '| Fichier | Lignes attendues | Debug (durée) | Release (durée) |',
              '|---|---:|---:|---:|']
    for file, (klass, method) in CORPUS_CONSUMERS.items():
        expected = counts[file] if counts is not None else None
        def proof_cell(variant):
            suite = suites[variant]
            receipt = suite.receipts.get(file, [])
            if (suite.methods.get(f'{klass}.{method}') == 'passed' and len(receipt) == 1
                    and (expected is None or receipt[0]['cases'] == expected)):
                return f'{receipt[0]["cases"]:,} lignes / {receipt[0]["elapsed_ms"]} ms'.replace(',', ' ')
            return 'Non prouvé'
        lines.append(f'| `{file}` | {expected if expected is not None else "—"} | {proof_cell("debug")} | {proof_cell("release")} |')
    lines += ['', 'Le corpus est exécuté **à l’intérieur** des tests unitaires ; ne pas additionner '
              'ses scénarios aux méthodes JUnit. Les exécutions Debug et Release '
              'sont comptées séparément, même lorsque leurs méthodes sont identiques.',
              'Les cas rattachés à une méthode en échec sont **affectés**, pas nécessairement '
              'tous erronés. Sans rapport XML, aucun test n’est considéré comme réussi.', '']
    for name, suite in suites.items():
        if suite.failures:
            lines += [f'### Échecs — {name}', '']
            lines += [f'- `{md_cell(f)}`' for f in suite.failures[:20]]
            lines.append('')
        if suite.warnings:
            lines += [f'### XML illisibles — {name}', '']
            lines += [f'- `{md_cell(w)}`' for w in suite.warnings[:10]]
            lines.append('')
    return '\n'.join(lines) + '\n'


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--debug', type=Path, default=Path('app/build/test-results/testDebugUnitTest'))
    parser.add_argument('--release', type=Path, default=Path('app/build/test-results/testReleaseUnitTest'))
    parser.add_argument('--android', type=Path, default=Path('app/build/outputs/androidTest-results/connected'))
    parser.add_argument('--manifest', type=Path, default=Path('app/src/test/resources/weather_regression/v1/manifest.json'))
    parser.add_argument('--python-log', type=Path)
    parser.add_argument('--summary', type=Path)
    parser.add_argument('--markdown', type=Path)
    parser.add_argument('--json', type=Path)
    parser.add_argument('--require-corpus', choices=('debug', 'release', 'both'),
                        help='Fail CI unless all fixture rows have verified JVM execution receipts')
    args = parser.parse_args(argv)
    counts = read_corpus_manifest(args.manifest)
    suites = {key: scan_junit(path) for key, path in
              [('debug', args.debug), ('release', args.release), ('android', args.android)]}
    corpus = {key: corpus_status(suites[key], counts) for key in ('debug', 'release')}
    python = python_status(args.python_log)
    markdown = render(suites, corpus, python, counts)
    print(markdown)
    if args.summary:
        args.summary.parent.mkdir(parents=True, exist_ok=True)
        with args.summary.open('a', encoding='utf-8') as fh:
            fh.write(markdown)
    if args.markdown:
        args.markdown.parent.mkdir(parents=True, exist_ok=True)
        args.markdown.write_text(markdown, encoding='utf-8')
    if args.json:
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(json.dumps({
            'unit_debug': asdict(suites['debug']), 'unit_release': asdict(suites['release']),
            'android': asdict(suites['android']), 'corpus': corpus, 'python': python,
        }, ensure_ascii=False, indent=2), encoding='utf-8')
    if args.require_corpus:
        variants = ('debug', 'release') if args.require_corpus == 'both' else (args.require_corpus,)
        for variant in variants:
            if corpus[variant]['validated'] != corpus[variant]['total']:
                print(f'CORPUS EXECUTION NOT PROVEN: {variant}: '
                      f'{corpus[variant]["validated"]}/{corpus[variant]["total"]} verified')
                return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
