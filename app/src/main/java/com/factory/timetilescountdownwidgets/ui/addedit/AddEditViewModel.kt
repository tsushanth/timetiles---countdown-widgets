package com.factory.timetilescountdownwidgets.ui.addedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.factory.timetilescountdownwidgets.data.CountdownEvent
import com.factory.timetilescountdownwidgets.data.CountdownRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AddEditUiState(
    val eventId: Long? = null,
    val title: String = "",
    val emoji: String = "🎉",
    val colorHex: String = "#6750A4",
    val targetDateTime: Long = System.currentTimeMillis() + 86_400_000L,
    val repeatsYearly: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastNotifiedInstant: Long = 0L,
    val isSaved: Boolean = false,
    val isLoading: Boolean = true,
    val loadError: Boolean = false,
    val saveErrorMessage: String? = null
)

class AddEditViewModel(
    private val repository: CountdownRepository,
    eventId: Long?
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditUiState(eventId = eventId))
    val uiState: StateFlow<AddEditUiState> = _uiState.asStateFlow()

    init {
        if (eventId != null) {
            viewModelScope.launch {
                repository.getById(eventId)?.let { event ->
                    _uiState.value = AddEditUiState(
                        eventId = event.id,
                        title = event.title,
                        emoji = event.emoji,
                        colorHex = event.colorHex,
                        targetDateTime = event.targetDateTime,
                        repeatsYearly = event.repeatsYearly,
                        createdAt = event.createdAt,
                        lastNotifiedInstant = event.lastNotifiedInstant,
                        isLoading = false
                    )
                } ?: run {
                    _uiState.value = _uiState.value.copy(isLoading = false, loadError = true)
                }
            }
        } else {
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun updateTitle(title: String) {
        _uiState.value = _uiState.value.copy(title = title)
    }

    fun updateEmoji(emoji: String) {
        _uiState.value = _uiState.value.copy(emoji = emoji)
    }

    fun updateColor(colorHex: String) {
        _uiState.value = _uiState.value.copy(colorHex = colorHex)
    }

    fun updateTargetDateTime(millis: Long) {
        _uiState.value = _uiState.value.copy(targetDateTime = millis)
    }

    fun updateRepeatsYearly(repeats: Boolean) {
        _uiState.value = _uiState.value.copy(repeatsYearly = repeats)
    }

    fun save() {
        val state = _uiState.value
        if (state.title.isBlank()) return
        viewModelScope.launch {
            val event = CountdownEvent(
                id = state.eventId ?: 0,
                title = state.title.trim(),
                emoji = state.emoji,
                colorHex = state.colorHex,
                targetDateTime = state.targetDateTime,
                repeatsYearly = state.repeatsYearly,
                lastNotifiedInstant = state.lastNotifiedInstant,
                createdAt = state.createdAt
            )
            try {
                if (state.eventId == null) {
                    repository.insert(event)
                } else {
                    repository.update(event)
                }
                _uiState.value = _uiState.value.copy(isSaved = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    saveErrorMessage = "Couldn't save your countdown. Please try again."
                )
            }
        }
    }

    fun dismissSaveError() {
        _uiState.value = _uiState.value.copy(saveErrorMessage = null)
    }

    class Factory(
        private val repository: CountdownRepository,
        private val eventId: Long?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AddEditViewModel(repository, eventId) as T
        }
    }
}
