package com.meteocompare.app.ui.citydetail

/*
 * ═══════════════════════════════════════════════════════════════════════════
 *  FICHIER OBSOLÈTE — À SUPPRIMER
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Le composable TemperatureComparisonChart (graphe des min/max par modèle sur
 * 7 jours) a été retiré : le besoin de comparaison inter-modèles est désormais
 * couvert de manière plus synthétique par la bande de confiance horaire
 * (ConfidenceBandSection dans HourlyConfidenceChart.kt), qui offre en plus les
 * variantes précipitation et vent via un sélecteur segmenté à 3 états.
 *
 * ─── Pourquoi ce fichier existe encore ? ──────────────────────────────────
 * Un `unzip` sur l'arbre projet n'efface pas les fichiers absents de l'archive.
 * Pour éviter que l'ancienne version de ce fichier — qui référençait des
 * strings et une fonction A11yFormatter désormais retirés — ne casse la
 * compilation, on distribue ce stub vide qui remplace proprement l'original.
 *
 * ─── Action requise ────────────────────────────────────────────────────────
 * Vous pouvez supprimer ce fichier en toute sécurité, il n'est référencé
 * nulle part dans le projet. Sur Windows (PowerShell) :
 *
 *     Remove-Item app\src\main\java\com\meteocompare\app\ui\citydetail\TemperatureComparisonChart.kt
 *
 * Sur Unix/macOS :
 *
 *     rm app/src/main/java/com/meteocompare/app/ui/citydetail/TemperatureComparisonChart.kt
 */
