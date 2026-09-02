package com.factory.timetilescountdownwidgets.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.factory.timetilescountdownwidgets.data.CountdownEvent
import com.factory.timetilescountdownwidgets.data.CountdownRepository
import com.factory.timetilescountdownwidgets.util.effectiveTargetMillis
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CountdownUiModel(
    val event: CountdownEvent,
    val effectiveTargetMillis: Long
)

class HomeViewModel(private val repository: CountdownRepository) : ViewModel() {

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            kotlinx.coroutines.delay(1000)
        }
    }

    val uiState: StateFlow<List<CountdownUiModel>> =
        combine(repository.observeAll(), ticker) { events, now ->
            events.map { event ->
                CountdownUiModel(event, effectiveTargetMillis(event, now))
            }.sortedBy { it.effectiveTargetMillis }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteEvent(event: CountdownEvent) {
        viewModelScope.launch { repository.delete(event) }
    }

    class Factory(private val repository: CountdownRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(repository) as T
        }
    }
}
