#!/usr/bin/env python3
"""Publish Android instrumented-test failures in the GitHub Actions job summary.

Reads the XML produced by Gradle's connectedAndroidTest task. This tool does not
change the test outcome and intentionally works even if reports are missing.
"""

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def _local(tag):
    return tag.rsplit('}', 1)[-1]


def _sanitize(value, limit=280):
    value = re.sub(r'\x1b\[[0-9;]*m', '', value or '')
    value = ' '.join(value.split())
    return (value[: limit - 1] + '…') if len(value) > limit else value


def _cell(value):
    return _sanitize(value).replace('\\', '\\\\').replace('|', '\\|').replace('`', "'")


def read_results(directory):
    """Return (test count, failures, skipped, report files, XML warnings)."""
    failures = []
    tests = skipped = 0
    reports = []
    warnings = []
    if not directory.exists():
        return tests, failures, skipped, reports, warnings

    for path in sorted(directory.rglob('*.xml')):
        try:
            root = ET.parse(path).getroot()
        except (ET.ParseError, OSError) as exc:
            warnings.append(f'{path}: {exc}')
            continue
        cases = [node for node in root.iter() if _local(node.tag) == 'testcase']
        if not cases:
            continue
        reports.append(path)
        for case in cases:
            tests += 1
            if any(_local(child.tag) == 'skipped' for child in case):
                skipped += 1
            for child in case:
                if _local(child.tag) not in {'failure', 'error'}:
                    continue
                full_text = child.attrib.get('message') or child.text or ''
                detail = child.text or ''
                failures.append((
                    case.attrib.get('classname', ''),
                    case.attrib.get('name', 'unknown'),
                    _sanitize(full_text or detail),
                    _sanitize(detail, limit=550),
                ))
    return tests, failures, skipped, reports, warnings


def format_summary(directory, result, limit=30):
    tests, failures, skipped, reports, warnings = result
    lines = ['## Tests Android instrumentés — API 35', '']
    if not reports:
        lines += [
            'Aucun rapport XML JUnit n’a été trouvé.',
            '',
            f'Répertoire attendu : `{directory.as_posix()}`',
            '',
            'Vérifier le journal de compilation et télécharger l’artefact de rapports.',
        ]
    else:
        lines += [
            f'**{tests} tests trouvés ; {len(failures)} échecs/erreurs ; {skipped} ignorés.**',
            '',
        ]
        if failures:
            lines += ['| Classe | Test | Cause |', '|---|---|---|']
            for classname, name, message, _ in failures[:limit]:
                lines.append(f'| {_cell(classname)} | {_cell(name)} | {_cell(message)} |')
            if len(failures) > limit:
                lines += ['', f'… et {len(failures) - limit} autres échecs.']
            lines.append('')
            for classname, name, message, detail in failures[:min(limit, 10)]:
                lines += [f'<details><summary>{_sanitize(classname + "." + name, 200)}</summary>',
                          '', '```text', (detail or message).replace('```', "'''"),
                          '```', '</details>', '']
        else:
            lines += ['Aucun échec présent dans les fichiers XML analysés.', '']
    if warnings:
        lines += ['### Fichiers XML illisibles', '']
        lines += [f'- `{_sanitize(warning, 350)}`' for warning in warnings[:10]]
        lines.append('')
    lines += [
        'Rapports HTML et XML complets : artefact du workflow '
        '**`androidTest-reports`** (ou **`release-androidTest-reports`**).',
        '',
    ]
    return '\n'.join(lines)


def annotation_escape(value):
    """GitHub workflow command escaping to avoid command injection/newlines."""
    return _sanitize(value, 350).replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--results', type=Path, default=Path('app/build/outputs/androidTest-results/connected'))
    parser.add_argument('--summary', type=Path, help='GitHub job summary Markdown output')
    args = parser.parse_args(argv)

    result = read_results(args.results)
    summary = format_summary(args.results, result)
    print(summary)
    if args.summary:
        args.summary.parent.mkdir(parents=True, exist_ok=True)
        with args.summary.open('a', encoding='utf-8') as dest:
            dest.write(summary + '\n')
    for classname, name, message, _ in result[1][:30]:
        print(f'::error title=AndroidTest {annotation_escape(name)}::{annotation_escape(classname + "." + name + ": " + message)}')
    # The Gradle task retains ownership of the job's pass/fail status.
    return 0


if __name__ == '__main__':
    sys.exit(main())
