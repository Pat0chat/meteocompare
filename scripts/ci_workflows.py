"""Lecture des workflows GitHub Actions pour les tests de contrat.

Les workflows délèguent des étapes à des workflows réutilisables
(`uses: ./.github/workflows/…`) et à des actions composites
(`uses: ./.github/actions/…`). Pour vérifier qu'un pipeline exécute bien une
étape, il faut donc lire le workflow ET tout ce qu'il appelle : c'est ce que
fait `expanded_workflow`. Aucune dépendance YAML n'est nécessaire.
"""
from __future__ import annotations

import re
from pathlib import Path
from typing import Dict, List, Optional, Set

ROOT = Path(__file__).resolve().parents[1]
WORKFLOWS = ROOT / '.github' / 'workflows'
ACTIONS = ROOT / '.github' / 'actions'
LOCAL_USES = re.compile(r'^\s*(?:-\s*)?uses:\s*\./(\.github/[\w./-]+?)\s*$', re.MULTILINE)


def local_targets(text: str) -> List[Path]:
    """Fichiers locaux appelés par un workflow ou une action (dans l'ordre)."""
    targets = []
    for relative in LOCAL_USES.findall(text):
        path = ROOT / relative
        targets.append(path if path.suffix in ('.yml', '.yaml') else path / 'action.yml')
    return targets


def expanded_workflow(name: str, _seen: Optional[Set[Path]] = None) -> str:
    """Texte du workflow suivi de celui de chaque workflow ou action local appelé."""
    seen = _seen if _seen is not None else set()
    path = WORKFLOWS / name if not Path(name).is_absolute() else Path(name)
    if path in seen:
        return ''
    seen.add(path)
    text = path.read_text(encoding='utf-8')
    parts = [text]
    for target in local_targets(text):
        if target.exists():
            parts.append(expanded_workflow(str(target), seen))
    return '\n'.join(parts)


def top_level_jobs(text: str) -> Dict[str, str]:
    """Découpe un workflow en blocs de jobs (indentation de deux espaces sous `jobs:`)."""
    body = text.split('\njobs:\n', 1)[1] if '\njobs:\n' in text else ''
    jobs: Dict[str, str] = {}
    current = None
    for line in body.splitlines():
        match = re.match(r'^  ([\w-]+):\s*$', line)
        if match:
            current = match.group(1)
            jobs[current] = ''
        elif current is not None:
            jobs[current] += line + '\n'
    return jobs


def job_needs(job_text: str) -> List[str]:
    match = re.search(r'^    needs:\s*\[([^\]]*)\]', job_text, re.MULTILINE)
    if match:
        return [item.strip() for item in match.group(1).split(',') if item.strip()]
    match = re.search(r'^    needs:\s*([\w-]+)\s*$', job_text, re.MULTILINE)
    return [match.group(1)] if match else []


def all_workflow_and_action_files() -> List[Path]:
    return sorted(WORKFLOWS.glob('*.yml')) + sorted(ACTIONS.glob('*/action.yml'))
