package com.factory.timetilescountdownwidgets.ui.detail

import app.cash.turbine.test
import com.factory.timetilescountdownwidgets.data.CountdownEvent
import com.factory.timetilescountdownwidgets.data.CountdownRepository
import com.factory.timetilescountdownwidgets.testutil.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: CountdownRepository
    private lateinit var eventFlow: MutableStateFlow<CountdownEvent?>

    private val eventId = 1L

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
        eventFlow = MutableStateFlow(null)
        every { repository.observeById(eventId) } returns eventFlow
    }

    private fun sampleEvent(id: Long = eventId) = CountdownEvent(
        id = id,
        title = "Trip",
        emoji = "✈️",
        colorHex = "#222222",
        targetDateTime = 5000L,
        repeatsYearly = false,
        createdAt = 0L
    )

    @Test
    fun `uiState starts with no event then reflects repository updates`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = DetailViewModel(repository, eventId)
            viewModel.uiState.test {
                assertNull(awaitItem().event)

                eventFlow.value = sampleEvent()
                val updated = awaitItem()
                assertEquals("Trip", updated.event?.title)
            }
        }

    @Test
    fun `delete removes the loaded event and invokes the callback`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = DetailViewModel(repository, eventId)
            // Subscribe once so the WhileSubscribed upstream combine runs and the event loads
            // into uiState.value before delete() reads it synchronously.
            viewModel.uiState.test {
                awaitItem() // initial state, event still null
                eventFlow.value = sampleEvent()
                awaitItem() // event now loaded
            }

            var deleted = false
            viewModel.delete { deleted = true }

            coVerify(exactly = 1) { repository.delete(sampleEvent()) }
            assertTrue(deleted)
        }

    @Test
    fun `delete with nothing loaded still invokes the callback without deleting`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = DetailViewModel(repository, eventId)
            var deleted = false

            viewModel.delete { deleted = true }

            coVerify(exactly = 0) { repository.delete(any()) }
            assertTrue(deleted)
        }
}
