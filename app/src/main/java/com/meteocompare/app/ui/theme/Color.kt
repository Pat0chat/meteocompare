package com.meteocompare.app.ui.theme

import androidx.compose.ui.graphics.Color

// Palette dérivée d'un bleu météo. À ajuster lors du polish UI.
val Primary80 = Color(0xFF90CAF9)
val Secondary80 = Color(0xFF80DEEA)
val Tertiary80 = Color(0xFFFFAB91)

val Primary40 = Color(0xFF0277BD)
val Secondary40 = Color(0xFF00838F)
val Tertiary40 = Color(0xFFD84315)

// Couleurs sémantiques par modèle (utilisées pour différencier les courbes superposées).
val ModelArome = Color(0xFF1976D2)
val ModelArpege = Color(0xFF388E3C)
val ModelIcon = Color(0xFFE64A19)
val ModelGfs = Color(0xFF7B1FA2)
val ModelEcmwf = Color(0xFFFBC02D)

// Couleurs sémantiques pour les niveaux de confiance.
//
// Deux palettes : SATURÉES pour le thème clair (utilisées comme texte
// foncé sur fond clair), PASTEL pour le thème sombre (utilisées comme
// texte clair sur fond sombre). La palette pastel garde la même teinte
// mais une luminosité bien plus élevée — sans ça, un vert foncé sur un
// surfaceContainerLow sombre est quasi invisible (la teinte verte se
// confond avec la luminance proche du fond).
//
// Les noms sans suffixe restent la palette clair, pour ne pas casser les
// previews/tests qui les référencent directement. Le code applicatif
// passe par [confidenceColor] qui dispatch automatiquement.
val ConfidenceHigh = Color(0xFF2E7D32)   // vert foncé
val ConfidenceMedium = Color(0xFFEF6C00) // orange
val ConfidenceLow = Color(0xFFC62828)    // rouge foncé

val ConfidenceHighDark = Color(0xFF81C784)   // vert pastel
val ConfidenceMediumDark = Color(0xFFFFB74D) // orange pastel
val ConfidenceLowDark = Color(0xFFE57373)    // rouge pastel
