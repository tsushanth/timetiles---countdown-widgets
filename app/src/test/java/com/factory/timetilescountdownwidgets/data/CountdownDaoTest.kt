package com.factory.timetilescountdownwidgets.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class CountdownDaoTest {

    private lateinit var database: CountdownDatabase
    private lateinit var dao: CountdownDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CountdownDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.countdownDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun event(
        id: Long = 0,
        title: String = "Birthday",
        targetDateTime: Long = 1_000L,
        repeatsYearly: Boolean = false,
        createdAt: Long = 500L
    ) = CountdownEvent(
        id = id,
        title = title,
        emoji = "🎉",
        colorHex = "#6750A4",
        targetDateTime = targetDateTime,
        repeatsYearly = repeatsYearly,
        createdAt = createdAt
    )

    @Test
    fun `insert then getById returns the inserted event`() = runTest {
        val id = dao.insert(event(title = "Anniversary"))
        val loaded = dao.getById(id)
        assertEquals("Anniversary", loaded?.title)
    }

    @Test
    fun `getById returns null for a missing id`() = runTest {
        assertNull(dao.getById(999L))
    }

    @Test
    fun `observeAll emits events ordered by targetDateTime ascending`() = runTest {
        dao.insert(event(title = "Later", targetDateTime = 3000))
        dao.insert(event(title = "Sooner", targetDateTime = 1000))
        dao.insert(event(title = "Middle", targetDateTime = 2000))

        dao.observeAll().test {
            assertEquals(listOf("Sooner", "Middle", "Later"), awaitItem().map { it.title })
        }
    }

    @Test
    fun `observeAll emits an updated list after an insert`() = runTest {
        dao.observeAll().test {
            assertEquals(emptyList(), awaitItem())
            dao.insert(event(title = "New"))
            assertEquals(listOf("New"), awaitItem().map { it.title })
        }
    }

    @Test
    fun `observeById reflects the current row and updates on change`() = runTest {
        val id = dao.insert(event(title = "Original"))
        dao.observeById(id).test {
            assertEquals("Original", awaitItem()?.title)
            dao.update(event(id = id, title = "Updated"))
            assertEquals("Updated", awaitItem()?.title)
        }
    }

    @Test
    fun `observeById emits null for an id that does not exist`() = runTest {
        dao.observeById(123L).test {
            assertNull(awaitItem())
        }
    }

    @Test
    fun `getAllOnce returns every inserted event`() = runTest {
        dao.insert(event(title = "A"))
        dao.insert(event(title = "B"))

        val all = dao.getAllOnce()

        assertEquals(2, all.size)
        assertEquals(setOf("A", "B"), all.map { it.title }.toSet())
    }

    @Test
    fun `update persists changed fields`() = runTest {
        val id = dao.insert(event(title = "Old", repeatsYearly = false))
        dao.update(event(id = id, title = "New", repeatsYearly = true))

        val loaded = dao.getById(id)
        assertEquals("New", loaded?.title)
        assertTrue(loaded!!.repeatsYearly)
    }

    @Test
    fun `delete removes the event`() = runTest {
        val id = dao.insert(event(title = "ToDelete"))
        val inserted = dao.getById(id)!!

        dao.delete(inserted)

        assertNull(dao.getById(id))
    }

    @Test
    fun `deleteById removes the event by id`() = runTest {
        val id = dao.insert(event(title = "ToDelete"))

        dao.deleteById(id)

        assertNull(dao.getById(id))
        assertEquals(0, dao.getAllOnce().size)
    }
}
