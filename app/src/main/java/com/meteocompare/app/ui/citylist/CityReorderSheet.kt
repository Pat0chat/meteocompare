package com.meteocompare.app.ui.citylist

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.util.FavoriteCityOrder

internal const val TAG_REORDER_SHEET = "home_reorder_sheet"
internal const val TAG_REORDER_SAVE = "home_reorder_save"
internal const val TAG_REORDER_UP = "home_reorder_up_"
internal const val TAG_REORDER_DOWN = "home_reorder_down_"

/** A compact, accessible editor: drag a handle or use the arrow buttons. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CityReorderSheet(
    cities: List<City>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    val order = remember { mutableStateListOf<String>().also { ids -> ids.addAll(cities.map(City::id)) } }
    val byId = cities.associateBy(City::id)
    val move: (String, Int) -> Unit = { id, direction ->
        val next = FavoriteCityOrder.move(order.toList(), id, direction)
        if (next != order.toList()) {
            order.clear()
            order.addAll(next)
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag(TAG_REORDER_SHEET)
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp)) {
            Text(stringResource(R.string.home_reorder_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.home_reorder_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.heightIn(max = 380.dp)
            ) {
                items(order, key = { it }) { cityId ->
                    val city = byId[cityId] ?: return@items
                    val index = order.indexOf(cityId)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.animateItem()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                city.name,
                                maxLines = 2,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { move(cityId, -1) },
                                enabled = index > 0,
                                modifier = Modifier.testTag(TAG_REORDER_UP + cityId)
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.home_reorder_up, city.name))
                            }
                            IconButton(
                                onClick = { move(cityId, 1) },
                                enabled = index < order.lastIndex,
                                modifier = Modifier.testTag(TAG_REORDER_DOWN + cityId)
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.home_reorder_down, city.name))
                            }
                            val currentMove by rememberUpdatedState(move)
                            val distance = with(LocalDensity.current) { 54.dp.toPx() }
                            var dragged by remember(cityId) { mutableFloatStateOf(0f) }
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .pointerInput(cityId) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { dragged = 0f },
                                            onDragEnd = { dragged = 0f },
                                            onDragCancel = { dragged = 0f },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragged += amount.y
                                                if (dragged >= distance) {
                                                    currentMove(cityId, 1)
                                                    dragged = 0f
                                                } else if (dragged <= -distance) {
                                                    currentMove(cityId, -1)
                                                    dragged = 0f
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.DragIndicator,
                                    contentDescription = stringResource(R.string.home_reorder_drag, city.name),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = { onSave(order.toList()) }, modifier = Modifier.testTag(TAG_REORDER_SAVE)) {
                    Text(stringResource(R.string.home_reorder_apply))
                }
            }
        }
    }
}
