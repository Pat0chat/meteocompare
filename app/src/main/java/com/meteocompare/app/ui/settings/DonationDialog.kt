package com.meteocompare.app.ui.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.meteocompare.app.R

/**
 * Dialog des plateformes de don.
 *
 * Politique :
 *   - Lien web vers le navigateur seulement, jamais de Play Billing
 *     (interdit par Google Play pour les apps non-charity).
 *   - Aucun privilège accordé aux donateurs — pas de "pro version".
 *   - Les URLs contiennent `USERNAME` placeholder — à remplacer par
 *     les vrais handles avant publication.
 *
 * Les liens sont ouverts via Intent.ACTION_VIEW, ce qui permet à
 * l'utilisateur de choisir son navigateur (cohérent avec ses
 * préférences système).
 */
@Composable
fun DonationDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current

    fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        // FLAG_ACTIVITY_NEW_TASK n'est pas strictement nécessaire ici
        // (LocalContext est l'activity), mais le rend explicite pour
        // les linters statiques.
        context.startActivity(intent)
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.donations_dialog_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.donations_dialog_body),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))

                DonationPlatformRow(
                    name = "Liberapay",
                    description = stringResource(R.string.donations_liberapay_desc),
                    onClick = { openUrl("https://liberapay.com/Pat0chat") }
                )
                HorizontalDivider()
                DonationPlatformRow(
                    name = "GitHub Sponsors",
                    description = stringResource(R.string.donations_github_sponsors_desc),
                    onClick = { openUrl("https://github.com/sponsors/Pat0chat") }
                )
                HorizontalDivider()
                DonationPlatformRow(
                    name = "Ko-Fi",
                    description = stringResource(R.string.donations_kofi_desc),
                    onClick = { openUrl("https://ko-fi.com/pat0chat") }
                )

                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.donations_dialog_disclaimer),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.donations_dialog_close))
            }
        }
    )
}

@Composable
private fun DonationPlatformRow(
    name: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.padding(end = 8.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
