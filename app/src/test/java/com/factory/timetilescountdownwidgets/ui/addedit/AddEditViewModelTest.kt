package com.factory.timetilescountdownwidgets.ui.addedit

import com.factory.timetilescountdownwidgets.data.CountdownEvent
import com.factory.timetilescountdownwidgets.data.CountdownRepository
import com.factory.timetilescountdownwidgets.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AddEditViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: CountdownRepository

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
    }

    private fun existingEvent(id: Long = 42L) = CountdownEvent(
        id = id,
        title = "Existing",
        emoji = "🎂",
        colorHex = "#111111",
        targetDateTime = 5000L,
        repeatsYearly = true,
        lastNotifiedInstant = 10L,
        createdAt = 1L
    )

    @Test
    fun `new event starts with defaults and is not loading`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = AddEditViewModel(repository, eventId = null)
        val state = viewModel.uiState.value

        assertNull(state.eventId)
        assertFalse(state.isLoading)
        assertFalse(state.loadError)
        assertFalse(state.isSaved)
    }

    @Test
    fun `existing event id loads fields from the repository`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { repository.getById(42L) } returns existingEvent()

        val viewModel = AddEditViewModel(repository, eventId = 42L)
        val state = viewModel.uiState.value

        assertEquals("Existing", state.title)
        assertEquals(true, state.repeatsYearly)
        assertFalse(state.isLoading)
        assertFalse(state.loadError)
    }

    @Test
    fun `missing event id sets loadError and stops loading`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { repository.getById(42L) } returns null

        val viewModel = AddEditViewModel(repository, eventId = 42L)
        val state = viewModel.uiState.value

        assertTrue(state.loadError)
        assertFalse(state.isLoading)
    }

    @Test
    fun `update functions mutate their corresponding state fields`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = AddEditViewModel(repository, eventId = null)

        viewModel.updateTitle("Party")
        viewModel.updateEmoji("🎈")
        viewModel.updateColor("#ABCDEF")
        viewModel.updateTargetDateTime(9999L)
        viewModel.updateRepeatsYearly(true)

        val state = viewModel.uiState.value
        assertEquals("Party", state.title)
        assertEquals("🎈", state.emoji)
        assertEquals("#ABCDEF", state.colorHex)
        assertEquals(9999L, state.targetDateTime)
        assertTrue(state.repeatsYearly)
    }

    @Test
    fun `save with a blank title is a no-op`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = AddEditViewModel(repository, eventId = null)

        viewModel.updateTitle("   ")
        viewModel.save()

        coVerify(exactly = 0) { repository.insert(any()) }
        assertFalse(viewModel.uiState.value.isSaved)
    }

    @Test
    fun `save with a new event inserts and marks the state saved`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { repository.insert(any()) } returns 1L
        val viewModel = AddEditViewModel(repository, eventId = null)

        viewModel.updateTitle("New Event")
        viewModel.save()

        coVerify(exactly = 1) { repository.insert(any()) }
        assertTrue(viewModel.uiState.value.isSaved)
        assertNull(viewModel.uiState.value.saveErrorMessage)
    }

    @Test
    fun `save with an existing event id updates instead of inserting`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { repository.getById(42L) } returns existingEvent()
            val viewModel = AddEditViewModel(repository, eventId = 42L)

            viewModel.updateTitle("Renamed")
            viewModel.save()

            coVerify(exactly = 1) { repository.update(any()) }
            coVerify(exactly = 0) { repository.insert(any()) }
            assertTrue(viewModel.uiState.value.isSaved)
        }

    @Test
    fun `save failure sets a save error message and leaves isSaved false`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { repository.insert(any()) } throws RuntimeException("db error")
            val viewModel = AddEditViewModel(repository, eventId = null)

            viewModel.updateTitle("Oops")
            viewModel.save()

            assertEquals(
                "Couldn't save your countdown. Please try again.",
                viewModel.uiState.value.saveErrorMessage
            )
            assertFalse(viewModel.uiState.value.isSaved)
        }

    @Test
    fun `dismissSaveError clears the save error message`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { repository.insert(any()) } throws RuntimeException("db error")
        val viewModel = AddEditViewModel(repository, eventId = null)
        viewModel.updateTitle("Oops")
        viewModel.save()

        viewModel.dismissSaveError()

        assertNull(viewModel.uiState.value.saveErrorMessage)
    }
}
