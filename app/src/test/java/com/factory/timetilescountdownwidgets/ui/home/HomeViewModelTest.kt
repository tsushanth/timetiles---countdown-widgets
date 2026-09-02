package com.factory.timetilescountdownwidgets.ui.home

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

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: CountdownRepository
    private lateinit var eventsFlow: MutableStateFlow<List<CountdownEvent>>

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
        eventsFlow = MutableStateFlow(emptyList())
        every { repository.observeAll() } returns eventsFlow
    }

    private fun event(id: Long, title: String, targetDateTime: Long, repeatsYearly: Boolean = false) =
        CountdownEvent(
            id = id,
            title = title,
            emoji = "🎉",
            colorHex = "#6750A4",
            targetDateTime = targetDateTime,
            repeatsYearly = repeatsYearly,
            createdAt = 0L
        )

    @Test
    fun `uiState starts empty when repository has no events`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = HomeViewModel(repository)
        viewModel.uiState.test {
            assertEquals(emptyList(), awaitItem())
        }
    }

    @Test
    fun `uiState reflects repository events sorted by effective target time`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = HomeViewModel(repository)
            viewModel.uiState.test {
                assertEquals(emptyList(), awaitItem())

                eventsFlow.value = listOf(
                    event(1, "Later", targetDateTime = 3000),
                    event(2, "Sooner", targetDateTime = 1000),
                    event(3, "Middle", targetDateTime = 2000)
                )

                val updated = awaitItem()
                assertEquals(listOf("Sooner", "Middle", "Later"), updated.map { it.event.title })
            }
        }

    @Test
    fun `uiState updates again when repository emits a new list`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = HomeViewModel(repository)
        viewModel.uiState.test {
            assertEquals(emptyList(), awaitItem())

            eventsFlow.value = listOf(event(1, "First", targetDateTime = 1000))
            assertEquals(listOf("First"), awaitItem().map { it.event.title })

            eventsFlow.value = emptyList()
            assertEquals(emptyList(), awaitItem())
        }
    }

    @Test
    fun `deleteEvent delegates to repository`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = HomeViewModel(repository)
        val target = event(1, "ToDelete", targetDateTime = 1000)

        viewModel.deleteEvent(target)

        coVerify(exactly = 1) { repository.delete(target) }
    }
}
