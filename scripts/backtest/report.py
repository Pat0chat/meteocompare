"""Mise en forme Markdown des résultats du banc d'essai."""
from __future__ import annotations

from typing import List

LABELS = {
    'MULTI': 'Multi-consensus (app, défaut)',
    'SCENARIOS': 'Scénarios (app)',
    'CALIB_REANALYSE': 'Calibration (app, réanalyse)',
    'ADAPTIVE_REANALYSE': 'Adaptatif (app, réanalyse)',
    'CALIB_OBS': 'Calibration, profils sur stations',
    'ADAPTIVE_OBS': 'Adaptatif, profils sur stations',
    'DECROISSANT_OBS': 'Proposé : biais décroissant',
    'DECROISSANT_POIDS_OBS': 'Proposé : biais décroissant + poids',
    'DECROISSANT_POIDS_OBS_30J': 'Proposé : biais décroissant + poids, 30 jours (app)',
    'PROBA_RECALIBREE_VILLE': 'Proposé : probabilité recalibrée par ville, 30 j, réanalyse (app)',
    'EMPIRIQUE_CALIB_OBS': 'Proposé : fourchette empirique autour de la Calibration, 30 j (app)',
    'MEDIANE_SIMPLE': 'Repère : médiane simple',
    'ECMWF_SEUL': 'Repère : ECMWF IFS seul',
    'MEILLEUR_RECENT': 'Repère : meilleur modèle récent',
    'PROBA_RECALIBREE': 'Proposé : probabilité recalibrée',
    'EMPIRIQUE': 'Proposé : fourchette empirique',
}
VARIABLE_TITLES = {
    'tmax': ('Température maximale', '°C'),
    'tmin': ('Température minimale', '°C'),
    'wind': ('Vent moyen maximal', 'km/h'),
}


def _get(results: dict, variable: str, lead: int, method: str) -> dict:
    return results['metrics'].get(f'{variable}|{lead}|{method}', {'n': 0})


def _wins(results: dict, variable: str, lead: int, method: str, baseline: str = 'MULTI') -> str:
    wins = total = 0
    for city in results['config']['cities']:
        a = results['city_metrics'].get(f'{variable}|{lead}|{method}|{city}', {})
        b = results['city_metrics'].get(f'{variable}|{lead}|{baseline}|{city}', {})
        if a.get('n') and b.get('n'):
            total += 1
            wins += a['mae'] < b['mae'] - 1e-12
    return f'{wins}/{total}'


def _methods_present(results: dict, variable: str, lead: int, order) -> List[str]:
    return [m for m in order if _get(results, variable, lead, m).get('n')]


def render(results: dict) -> str:
    config = results['config']
    leads = config['leads']
    lines = [
        '# Banc d\'essai des moteurs MeteoCompare',
        '',
        f"Période évaluée : {config['start']} → {config['end']} · échéances J+{', J+'.join(map(str, leads))} · "
        f"{len(config['cities'])} stations · modèles : sélection par défaut de l'app.",
        '',
        'Vérité terrain : METAR horaires (Tmax, Tmin, vent) et GHCN-Daily (pluie). Erreur = prévision − observation.',
        '',
        '## La référence de calibration actuelle face aux stations',
        '',
        '| Variable | Écart moyen réanalyse − station | Écart absolu moyen | Jours |',
        '|---|---:|---:|---:|',
    ]
    for variable, label in (('tmax', 'Tmax (°C)'), ('tmin', 'Tmin (°C)'), ('wind', 'Vent max (km/h)'),
                            ('precip', 'Pluie (mm)')):
        gap = results['reference_gap'].get(variable, {})
        if gap.get('n'):
            lines.append(f"| {label} | {gap['bias']:+.2f} | {gap['mae']:.2f} | {gap['n']} |")
    lines.append('')

    order = ['MULTI', 'SCENARIOS', 'CALIB_REANALYSE', 'ADAPTIVE_REANALYSE', 'CALIB_OBS', 'ADAPTIVE_OBS',
             'DECROISSANT_OBS', 'DECROISSANT_POIDS_OBS', 'DECROISSANT_POIDS_OBS_30J', 'MEDIANE_SIMPLE', 'ECMWF_SEUL', 'MEILLEUR_RECENT']
    for variable, (title, unit) in VARIABLE_TITLES.items():
        lines += [f'## {title} — erreur absolue moyenne ({unit})', '',
                  'Entre parenthèses : écart relatif au Multi-consensus (négatif = mieux).', '',
                  '| Méthode | ' + ' | '.join(f'J+{l}' for l in leads) + ' | Villes gagnées J+1 | Biais J+1 |',
                  '|---|' + '---:|' * len(leads) + '---:|---:|']
        for method in order:
            cells = []
            for lead in leads:
                metric = _get(results, variable, lead, method)
                base = _get(results, variable, lead, 'MULTI')
                if not metric.get('n'):
                    cells.append('—')
                    continue
                delta = (metric['mae'] / base['mae'] - 1) * 100 if base.get('n') else 0.0
                cells.append(f"{metric['mae']:.2f}" + ('' if method == 'MULTI' else f' ({delta:+.0f} %)'))
            first = _get(results, variable, leads[0], method)
            wins = '—' if method == 'MULTI' else _wins(results, variable, leads[0], method)
            bias = f"{first['bias']:+.2f}" if first.get('n') else '—'
            lines.append(f'| {LABELS[method]} | ' + ' | '.join(cells) + f' | {wins} | {bias} |')
        lines.append('')

    lines += ['## Pluie', '', '### Probabilité de pluie — score de Brier (plus bas = mieux)', '',
              '| Méthode | ' + ' | '.join(f'J+{l}' for l in leads) + ' |', '|---|' + '---:|' * len(leads)]
    for method in ('MULTI', 'CALIB_REANALYSE', 'ADAPTIVE_REANALYSE', 'CALIB_OBS', 'ADAPTIVE_OBS', 'PROBA_RECALIBREE',
                   'PROBA_RECALIBREE_VILLE'):
        cells = []
        for lead in leads:
            metric = _get(results, 'precip_brier', lead, method)
            cells.append(f"{metric['rmse'] ** 2:.3f}" if metric.get('n') else '—')
        lines.append(f'| {LABELS[method]} | ' + ' | '.join(cells) + ' |')
    lines += ['', '### Quantité affichée — erreur absolue moyenne (mm)', '',
              '| Méthode | ' + ' | '.join(f'J+{l}' for l in leads) + ' |', '|---|' + '---:|' * len(leads)]
    for method in ('MULTI', 'CALIB_REANALYSE', 'CALIB_OBS'):
        cells = []
        for lead in leads:
            metric = _get(results, 'precip_mm', lead, method)
            cells.append(f"{metric['mae']:.2f}" if metric.get('n') else '—')
        lines.append(f'| {LABELS[method]} | ' + ' | '.join(cells) + ' |')
    shifts = {c: s for c, s in config.get('precip_shift_days', {}).items() if s is not None}
    if shifts:
        shifted = sorted(c for c, s in shifts.items() if s)
        lines += ['', f"Fenêtre GHCN décalée d'un jour pour : {', '.join(shifted) if shifted else 'aucune station'}."]
    lines.append('')

    lines += ['## Fourchettes : part des observations à l\'intérieur', '',
              'La fourchette de l\'app n\'a pas de niveau de confiance déclaré ; la fourchette empirique vise 80 %.', '',
              '| Variable | Fourchette | ' + ' | '.join(f'J+{l}' for l in leads) + ' | Largeur moyenne J+1 |',
              '|---|---|' + '---:|' * len(leads) + '---:|']
    for variable, (title, unit) in VARIABLE_TITLES.items():
        for kind in ('MULTI', 'CALIB_REANALYSE', 'EMPIRIQUE', 'EMPIRIQUE_CALIB_OBS'):
            cells = []
            for lead in leads:
                stat = results['intervals'].get(f'{variable}|{lead}|{kind}', {})
                cells.append(f"{stat['coverage'] * 100:.0f} %" if stat.get('n') else '—')
            first = results['intervals'].get(f'{variable}|{leads[0]}|{kind}', {})
            width = f"{first['mean_width']:.1f} {unit}" if first.get('n') else '—'
            lines.append(f'| {title} | {LABELS.get(kind, kind)} | ' + ' | '.join(cells) + f' | {width} |')
    lines.append('')
    return '\n'.join(lines)
