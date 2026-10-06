package com.meteocompare.app.ui.components

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.meteocompare.app.R
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import org.junit.Rule
import org.junit.Test

class AppToastLayerTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dispatcher_toast_survives_source_composable_removal() {
        val showSource = mutableStateOf(true)

        composeRule.setContent {
            MeteoCompareTheme {
                AppToastLayer {
                    if (showSource.value) {
                        val showToast = rememberAppToastDispatcher()
                        Button(
                            modifier = Modifier.testTag("toast_source_action"),
                            onClick = {
                                showToast(AppToastEvent.success(R.string.toast_models_updated))
                                showSource.value = false
                            }
                        ) {
                            Text("Save")
                        }
                    } else {
                        Text("Destination", modifier = Modifier.testTag("toast_destination"))
                    }
                }
            }
        }

        composeRule.onNodeWithTag("toast_source_action").performClick()
        composeRule.onNodeWithTag("toast_destination").assertIsDisplayed()
        composeRule.onNodeWithTag("$TAG_APP_TOAST-success").assertIsDisplayed()
    }
}
