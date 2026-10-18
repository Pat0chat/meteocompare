package com.meteocompare.app.ui.citylist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteocompare.app.R
import com.meteocompare.app.data.repository.CurrentLocationController
import com.meteocompare.app.ui.components.AppToastEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CurrentLocationViewModel @Inject constructor(
    private val controller: CurrentLocationController
) : ViewModel() {
    private val feedbackChannel = Channel<AppToastEvent>(Channel.BUFFERED)
    val feedback = feedbackChannel.receiveAsFlow()

    fun locate() {
        viewModelScope.launch {
            val result = controller.enable()
            val event = when (result) {
                CurrentLocationController.Result.Updated ->
                    AppToastEvent.success(R.string.current_location_updated)
                CurrentLocationController.Result.PermissionMissing ->
                    AppToastEvent.warning(R.string.current_location_permission_required)
                CurrentLocationController.Result.Unavailable ->
                    AppToastEvent.warning(R.string.current_location_unavailable)
                CurrentLocationController.Result.Unchanged -> null
            }
            if (event != null) feedbackChannel.send(event)
        }
    }
    fun refreshActive() {
        viewModelScope.launch {
            // A pull-to-refresh must NOT opt in to location tracking.
            controller.refreshExisting()
        }
    }
    fun denied() {
        viewModelScope.launch {
            feedbackChannel.send(AppToastEvent.warning(R.string.current_location_permission_required))
        }
    }
}
