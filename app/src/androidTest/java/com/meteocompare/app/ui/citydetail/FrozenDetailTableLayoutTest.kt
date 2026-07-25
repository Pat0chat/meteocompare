package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.fetchSemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FrozenDetailTableLayoutTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun temporal_headers_and_values_keep_the_same_x_after_horizontal_scroll() {
        composeRule.setContent {
            MeteoCompareTheme {
                val palette = detailTablePalette()
                FrozenDetailTableLayout(
                    modelColumnWidth = 79.dp,
                    temporalColumnCount = 12,
                    headerHeight = 40.dp,
                    rowHeight = 40.dp,
                    rowCount = 1,
                    palette = palette,
                    modifier = Modifier.width(320.dp).testTag("table"),
                    cornerHeader = { Box(Modifier.width(79.dp).height(40.dp)) },
                    temporalHeaders = {
                        repeat(12) { index ->
                            Box(Modifier.width(73.3.dp).height(40.dp).testTag("header-$index"))
                        }
                    },
                    modelRows = { Box(Modifier.width(79.dp).height(40.dp)) },
                    temporalColumns = {
                        repeat(12) { index ->
                            Column(Modifier.width(73.3.dp)) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .testTag("value-$index")
                                )
                            }
                        }
                    }
                )
            }
        }

        assertAligned(0)

        composeRule.onNodeWithTag("table").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertAligned(5)
    }

    private fun assertAligned(index: Int) {
        val header = composeRule.onNodeWithTag("header-$index").fetchSemanticsNode().boundsInRoot
        val value = composeRule.onNodeWithTag("value-$index").fetchSemanticsNode().boundsInRoot
        assertEquals(header.left, value.left, 1f)
        assertEquals(header.right, value.right, 1f)
    }
}
