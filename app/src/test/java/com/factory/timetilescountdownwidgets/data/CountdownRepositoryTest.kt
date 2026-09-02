package com.factory.timetilescountdownwidgets.data

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class CountdownRepositoryTest {

    private lateinit var dao: CountdownDao
    private lateinit var repository: CountdownRepository

    @Before
    fun setUp() {
        dao = mockk()
        repository = CountdownRepository(dao)
    }

    private fun event(id: Long = 1L) = CountdownEvent(
        id = id,
        title = "Test",
        emoji = "🎉",
        colorHex = "#FFFFFF",
        targetDateTime = 1000L,
        repeatsYearly = false,
        createdAt = 0L
    )

    @Test
    fun `observeAll delegates to dao`() = runTest {
        val flow = flowOf(listOf(event()))
        every { dao.observeAll() } returns flow

        assertEquals(flow, repository.observeAll())
    }

    @Test
    fun `observeById delegates to dao with the given id`() = runTest {
        val flow = flowOf(event())
        every { dao.observeById(5L) } returns flow

        assertEquals(flow, repository.observeById(5L))
        verify(exactly = 1) { dao.observeById(5L) }
    }

    @Test
    fun `getById returns the dao result`() = runTest {
        coEvery { dao.getById(5L) } returns event(5L)

        val result = repository.getById(5L)

        assertEquals(5L, result?.id)
    }

    @Test
    fun `getById propagates dao exceptions`() = runTest {
        coEvery { dao.getById(any()) } throws RuntimeException("db closed")

        assertFailsWith<RuntimeException> { repository.getById(1L) }
    }

    @Test
    fun `getAllOnce returns the dao result`() = runTest {
        coEvery { dao.getAllOnce() } returns listOf(event(1L), event(2L))

        val result = repository.getAllOnce()

        assertEquals(2, result.size)
    }

    @Test
    fun `insert delegates and returns the generated id`() = runTest {
        coEvery { dao.insert(any()) } returns 42L

        val id = repository.insert(event())

        assertEquals(42L, id)
        coVerify(exactly = 1) { dao.insert(any()) }
    }

    @Test
    fun `insert propagates dao exceptions`() = runTest {
        coEvery { dao.insert(any()) } throws IllegalStateException("disk full")

        assertFailsWith<IllegalStateException> { repository.insert(event()) }
    }

    @Test
    fun `update delegates to dao`() = runTest {
        coEvery { dao.update(any()) } just Runs

        repository.update(event())

        coVerify(exactly = 1) { dao.update(any()) }
    }

    @Test
    fun `delete delegates to dao`() = runTest {
        coEvery { dao.delete(any()) } just Runs

        repository.delete(event())

        coVerify(exactly = 1) { dao.delete(any()) }
    }

    @Test
    fun `deleteById delegates to dao`() = runTest {
        coEvery { dao.deleteById(9L) } just Runs

        repository.deleteById(9L)

        coVerify(exactly = 1) { dao.deleteById(9L) }
    }
}
