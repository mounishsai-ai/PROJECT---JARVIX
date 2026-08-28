package com.example.aisecretary.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aisecretary.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val ambientSoundEnabled: Boolean,
    val alarmSoundInSilentMode: Boolean,
    val snoozeMinutesShort: Int,
    val snoozeMinutesMedium: Int,
    val snoozeMinutesLong: Int,
)

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            ambientSoundEnabled = repository.ambientSoundEnabled,
            alarmSoundInSilentMode = repository.alarmSoundInSilentMode,
            snoozeMinutesShort = repository.snoozeMinutesShort,
            snoozeMinutesMedium = repository.snoozeMinutesMedium,
            snoozeMinutesLong = repository.snoozeMinutesLong,
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState

    fun setAmbientSoundEnabled(enabled: Boolean) {
        repository.ambientSoundEnabled = enabled
        _uiState.value = _uiState.value.copy(ambientSoundEnabled = enabled)
    }

    fun setAlarmSoundInSilentMode(enabled: Boolean) {
        repository.alarmSoundInSilentMode = enabled
        _uiState.value = _uiState.value.copy(alarmSoundInSilentMode = enabled)
    }

    /** Snooze minutes must stay positive and in ascending order - a 0m or out-of-order snooze
     *  button would be confusing, so invalid input is silently ignored rather than saved. */
    fun setSnoozeMinutesShort(minutes: Int) {
        if (minutes <= 0 || minutes >= _uiState.value.snoozeMinutesMedium) return
        viewModelScope.launch {
            repository.snoozeMinutesShort = minutes
            _uiState.value = _uiState.value.copy(snoozeMinutesShort = minutes)
        }
    }

    fun setSnoozeMinutesMedium(minutes: Int) {
        val s = _uiState.value
        if (minutes <= s.snoozeMinutesShort || minutes >= s.snoozeMinutesLong) return
        viewModelScope.launch {
            repository.snoozeMinutesMedium = minutes
            _uiState.value = _uiState.value.copy(snoozeMinutesMedium = minutes)
        }
    }

    fun setSnoozeMinutesLong(minutes: Int) {
        if (minutes <= _uiState.value.snoozeMinutesMedium) return
        viewModelScope.launch {
            repository.snoozeMinutesLong = minutes
            _uiState.value = _uiState.value.copy(snoozeMinutesLong = minutes)
        }
    }

    companion object {
        fun provideFactory(repository: SettingsRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                        return SettingsViewModel(repository) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
    }
}
