package com.meteocompare.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.ui.preview.MeteoComponentPreview
import com.meteocompare.app.ui.preview.MeteoPreviewSurface

@MeteoComponentPreview
@Composable
private fun CityLocationDetailsPreview() {
    MeteoPreviewSurface {
        CityLocationDetails(
            City(
                id = "saint-martin", name = "Saint-Martin",
                country = "France", admin1 = "Normandie",
                departmentName = "Manche", admin3 = "Avranches", postalCode = "50300",
                latitude = 48.68, longitude = -1.36
            ),
            Modifier.padding(16.dp)
        )
    }
}
