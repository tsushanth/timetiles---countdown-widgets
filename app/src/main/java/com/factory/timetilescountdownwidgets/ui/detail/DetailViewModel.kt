package com.factory.timetilescountdownwidgets.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.factory.timetilescountdownwidgets.data.CountdownEvent
import com.factory.timetilescountdownwidgets.data.CountdownRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DetailUiState(
    val event: CountdownEvent? = null,
    val now: Long = System.currentTimeMillis()
)

class DetailViewModel(
    private val repository: CountdownRepository,
    private val eventId: Long
) : ViewModel() {

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            kotlinx.coroutines.delay(1000)
        }
    }

    val uiState: StateFlow<DetailUiState> =
        combine(repository.observeById(eventId), ticker) { event, now ->
            DetailUiState(event, now)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DetailUiState()
        )

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            uiState.value.event?.let { repository.delete(it) }
            onDeleted()
        }
    }

    class Factory(
        private val repository: CountdownRepository,
        private val eventId: Long
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DetailViewModel(repository, eventId) as T
        }
    }
}
