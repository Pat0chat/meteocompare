package com.meteocompare.app.ui.citylist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Duo lever/coucher du soleil affiché sous l'identité de chaque CityCard.
 *
 * ─── Formatage HH:mm sensible à la locale ──────────────────────────────────
 * Utilise [DateTimeFormatter.ofLocalizedTime] en SHORT → HH:mm (24h) en FR,
 * "6:12 AM" en EN. Le formatter tient compte de la locale runtime du device
 * — pas besoin de dupliquer côté string resource.
 *
 * ─── A11y ──────────────────────────────────────────────────────────────────
 * Chaque icône est décorative (contentDescription null au niveau Icon), la
 * sémantique globale de la Row porte "Lever du soleil à 6:12, coucher à 21:45"
 * via `sun_times_a11y` avec 2 placeholders. TalkBack lit UNE phrase cohérente
 * plutôt que 2 icônes + 2 heures dans l'ordre visuel.
 *
 * ─── Cas dégénéré polaire ──────────────────────────────────────────────────
 * Si sunrise OU sunset est null (nuit polaire / soleil de minuit — cf.
 * [com.meteocompare.app.domain.util.SolarTimes]), on affiche un simple "—"
 * discret plutôt que de masquer complètement le composable. Le manque de
 * lever/coucher est une info UTILE en soi pour un utilisateur qui voyage
 * en région polaire.
 *
 * ─── Note d'implémentation : @Composable + .semantics ─────────────────────
 * Le label a11y est calculé DANS le corps du composable (via
 * `stringResource` qui EST @Composable), pas à l'intérieur du bloc
 * `.semantics { }` qui a un receiver SemanticsPropertyReceiver non-@Composable.
 * On capture le résultat dans une val locale, puis on le référence dans
 * .semantics — pattern standard Compose.
 */
@Composable
internal fun SunTimesRow(
    sunrise: LocalTime?,
    sunset: LocalTime?,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    // Résolution des textes AVANT la Row — on est encore en scope @Composable
    // ici, donc stringResource(...) est légal. À l'intérieur de .semantics { }
    // le scope est SemanticsPropertyReceiver et les appels @Composable y sont
    // interdits par le compilateur.
    val sunriseText = sunrise?.format(formatter) ?: "—"
    val sunsetText = sunset?.format(formatter) ?: "—"
    val a11yLabel = stringResource(R.string.sun_times_a11y, sunriseText, sunsetText)

    Surface(
        modifier = modifier
            .testTag(TAG_SUN_TIMES_ROW)
            .semantics {
                contentDescription = a11yLabel
            },
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.00f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.WbSunny,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = sunriseText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(5.dp))
            Icon(
                imageVector = Icons.Filled.Bedtime,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = sunsetText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** testTag utilisé par les instrumentation tests pour retrouver cette Row. */
internal const val TAG_SUN_TIMES_ROW = "sun_times_row"