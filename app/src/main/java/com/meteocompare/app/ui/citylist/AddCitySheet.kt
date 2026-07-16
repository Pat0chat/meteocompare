package com.meteocompare.app.ui.citylist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.City

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCitySheet(
    state: AddCityUiState,
    onQueryChanged: (String) -> Unit,
    onCitySelected: (City) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag(TAG_ADD_CITY_SHEET)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.action_add_city),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChanged,
                placeholder = { Text(stringResource(R.string.search_city_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_ADD_CITY_SEARCH_FIELD)
            )

            Spacer(Modifier.height(8.dp))

            // Contenu : trois états possibles
            when {
                state.error != null -> {
                    Box(modifier = Modifier.padding(16.dp).testTag(TAG_ADD_CITY_ERROR)) {
                        Text(
                            text = state.error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                state.isSearching -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .testTag(TAG_ADD_CITY_LOADING),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.query.length >= 2 && state.results.isEmpty() -> {
                    Box(modifier = Modifier.padding(16.dp).testTag(TAG_ADD_CITY_NO_RESULTS)) {
                        Text(
                            text = stringResource(R.string.search_no_results),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(state.results, key = { it.id }) { city ->
                            CityResultRow(city = city, onClick = { onCitySelected(city) })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CityResultRow(city: City, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("$TAG_ADD_CITY_RESULT${city.id}")
            .padding(vertical = 12.dp, horizontal = 4.dp)
    ) {
        Text(city.name, style = MaterialTheme.typography.bodyLarge)
        val subtitle = listOfNotNull(city.admin1, city.country.takeIf { it.isNotBlank() })
            .joinToString(", ")
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal const val TAG_ADD_CITY_SHEET = "add_city_sheet"
internal const val TAG_ADD_CITY_SEARCH_FIELD = "add_city_search_field"
internal const val TAG_ADD_CITY_LOADING = "add_city_loading"
internal const val TAG_ADD_CITY_ERROR = "add_city_error"
internal const val TAG_ADD_CITY_NO_RESULTS = "add_city_no_results"
internal const val TAG_ADD_CITY_RESULT = "add_city_result_"
