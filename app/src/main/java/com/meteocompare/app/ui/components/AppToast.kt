package com.meteocompare.app.ui.components

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.meteocompare.app.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal const val TAG_APP_TOAST = "app-toast"

/** Nature du retour utilisateur, indépendante de l'écran qui l'a produit. */
enum class AppToastType {
    SUCCESS,
    ERROR,
    WARNING,
    INFO
}

/** Durée métier traduite en durée Material uniquement par l'hôte global. */
enum class AppToastDuration {
    SHORT,
    LONG,
    INDEFINITE
}

/**
 * Événement de notification localisable.
 *
 * Les ViewModels conservent l'identifiant de ressource au lieu de résoudre le
 * texte avec un Context : un changement de langue intervenu avant l'affichage
 * utilise ainsi les ressources réellement actives.
 */
@Immutable
data class AppToastEvent(
    @param:StringRes val messageRes: Int,
    val formatArgs: List<Any> = emptyList(),
    val type: AppToastType = AppToastType.INFO,
    val duration: AppToastDuration = AppToastDuration.SHORT,
    @param:StringRes val actionLabelRes: Int? = null,
    val onAction: (() -> Unit)? = null
) {
    companion object {
        fun success(@StringRes messageRes: Int, vararg formatArgs: Any): AppToastEvent =
            AppToastEvent(
                messageRes = messageRes,
                formatArgs = formatArgs.toList(),
                type = AppToastType.SUCCESS
            )

        fun error(@StringRes messageRes: Int, vararg formatArgs: Any): AppToastEvent =
            AppToastEvent(
                messageRes = messageRes,
                formatArgs = formatArgs.toList(),
                type = AppToastType.ERROR,
                duration = AppToastDuration.LONG
            )

        fun warning(@StringRes messageRes: Int, vararg formatArgs: Any): AppToastEvent =
            AppToastEvent(
                messageRes = messageRes,
                formatArgs = formatArgs.toList(),
                type = AppToastType.WARNING,
                duration = AppToastDuration.LONG
            )

        fun info(@StringRes messageRes: Int, vararg formatArgs: Any): AppToastEvent =
            AppToastEvent(
                messageRes = messageRes,
                formatArgs = formatArgs.toList(),
                type = AppToastType.INFO
            )
    }
}

internal data class AppToastVisuals(
    override val message: String,
    override val actionLabel: String?,
    override val withDismissAction: Boolean,
    override val duration: SnackbarDuration,
    val type: AppToastType
) : SnackbarVisuals

@Stable
internal class AppToastHostState(
    internal val materialState: SnackbarHostState
) {
    suspend fun show(event: AppToastEvent, resources: Resources): SnackbarResult {
        val result = materialState.showSnackbar(
            AppToastVisuals(
                message = resources.getString(event.messageRes, *event.formatArgs.toTypedArray()),
                actionLabel = event.actionLabelRes?.let { resources.getString(it) },
                withDismissAction = event.type == AppToastType.ERROR ||
                    event.duration == AppToastDuration.INDEFINITE,
                duration = event.duration.toSnackbarDuration(),
                type = event.type
            )
        )
        if (result == SnackbarResult.ActionPerformed) event.onAction?.invoke()
        return result
    }
}

@Composable
internal fun rememberAppToastHostState(): AppToastHostState {
    val materialState = remember { SnackbarHostState() }
    return remember(materialState) { AppToastHostState(materialState) }
}

private val LocalAppToastHostState = staticCompositionLocalOf<AppToastHostState?> { null }

/**
 * Couche unique placée au-dessus de toute la navigation téléphone/tablette.
 * Tous les écrans partagent donc la même file et le même rendu.
 */
@Composable
internal fun AppToastLayer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val hostState = rememberAppToastHostState()
    CompositionLocalProvider(LocalAppToastHostState provides hostState) {
        Box(modifier = modifier.fillMaxSize()) {
            content()
            SnackbarHost(
                hostState = hostState.materialState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .statusBarsPadding()
                    .padding(bottom = 64.dp, start = 16.dp, end = 16.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .zIndex(10f)
            ) { data ->
                AppToast(data = data)
            }
        }
    }
}

/**
 * Branche un flux one-shot à l'hôte global. collectLatest évite qu'une rafale
 * de réglages rapides affiche plusieurs confirmations devenues obsolètes.
 */
@Composable
internal fun AppToastEffect(events: Flow<AppToastEvent>) {
    val hostState = LocalAppToastHostState.current ?: return
    val resources = LocalResources.current
    LaunchedEffect(events, hostState, resources) {
        events.collectLatest { event -> hostState.show(event, resources) }
    }
}

/** Point d'entrée pour les actions purement UI, comme l'ouverture d'un lien externe. */
@Composable
internal fun rememberAppToastDispatcher(): (AppToastEvent) -> Unit {
    val hostState = LocalAppToastHostState.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    return remember(hostState, resources, scope) {
        { event ->
            if (hostState != null) {
                scope.launch { hostState.show(event, resources) }
            }
        }
    }
}

@Composable
private fun AppToast(data: SnackbarData) {
    val visuals = data.visuals
    val appVisuals = visuals as? AppToastVisuals
    AppToastCard(
        message = visuals.message,
        type = appVisuals?.type ?: AppToastType.INFO,
        actionLabel = visuals.actionLabel,
        onAction = visuals.actionLabel?.let { { data.performAction() } },
        onDismiss = if (visuals.withDismissAction) data::dismiss else null
    )
}

/** Rendu public au module afin que les quatre états disposent de previews debug. */
@Composable
internal fun AppToastCard(
    message: String,
    type: AppToastType,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    val accent = appToastAccent(type)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(14.dp))
            .semantics {
                liveRegion = if (type == AppToastType.ERROR) {
                    LiveRegionMode.Assertive
                } else {
                    LiveRegionMode.Polite
                }
            }
            .testTag("$TAG_APP_TOAST-${type.name.lowercase()}"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.14f),
                contentColor = accent
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = appToastIcon(type),
                        contentDescription = null,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
            Spacer(Modifier.width(11.dp))
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (actionLabel != null && onAction != null) {
                TextButton(
                    onClick = onAction,
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = actionLabel,
                        color = accent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (onDismiss != null) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.toast_dismiss),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun appToastAccent(type: AppToastType): Color {
    val dark = isSystemInDarkTheme()
    return when (type) {
        AppToastType.SUCCESS -> if (dark) Color(0xFF81C784) else Color(0xFF2E7D32)
        AppToastType.ERROR -> MaterialTheme.colorScheme.error
        AppToastType.WARNING -> if (dark) Color(0xFFFFCC80) else Color(0xFF9A6700)
        AppToastType.INFO -> MaterialTheme.colorScheme.primary
    }
}

private fun appToastIcon(type: AppToastType): ImageVector = when (type) {
    AppToastType.SUCCESS -> Icons.Outlined.CheckCircle
    AppToastType.ERROR -> Icons.Outlined.ErrorOutline
    AppToastType.WARNING -> Icons.Outlined.WarningAmber
    AppToastType.INFO -> Icons.Outlined.Info
}

private fun AppToastDuration.toSnackbarDuration(): SnackbarDuration = when (this) {
    AppToastDuration.SHORT -> SnackbarDuration.Short
    AppToastDuration.LONG -> SnackbarDuration.Long
    AppToastDuration.INDEFINITE -> SnackbarDuration.Indefinite
}
