package com.meteocompare.app.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * Certains launchers ouvrent l'activité de configuration dans un contexte où
 * le champ Compose reçoit le focus mais où l'IME reste caché.
 * Relancer l'ouverture APRÈS la prise de focus laisse à BasicTextField
 * le temps de créer sa session de saisie. Ne force aucun clavier à l'ouverture
 * de la page ou de la boîte de dialogue.
 */
@Composable
internal fun Modifier.showWidgetKeyboardOnFocus(): Modifier {
    val keyboardController = LocalSoftwareKeyboardController.current
    var hasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(hasFocus) {
        if (hasFocus) {
            // Laisser le champ démarrer sa session de saisie avant SHOW_IME.
            withFrameNanos { }
            keyboardController?.show()
        }
    }
    return this.onFocusChanged { hasFocus = it.isFocused }
}
