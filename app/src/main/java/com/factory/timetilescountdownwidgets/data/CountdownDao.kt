package com.factory.timetilescountdownwidgets.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CountdownDao {
    @Query("SELECT * FROM countdown_events ORDER BY targetDateTime ASC")
    fun observeAll(): Flow<List<CountdownEvent>>

    @Query("SELECT * FROM countdown_events WHERE id = :id")
    fun observeById(id: Long): Flow<CountdownEvent?>

    @Query("SELECT * FROM countdown_events WHERE id = :id")
    suspend fun getById(id: Long): CountdownEvent?

    @Query("SELECT * FROM countdown_events")
    suspend fun getAllOnce(): List<CountdownEvent>

    @Insert
    suspend fun insert(event: CountdownEvent): Long

    @Update
    suspend fun update(event: CountdownEvent)

    @Delete
    suspend fun delete(event: CountdownEvent)

    @Query("DELETE FROM countdown_events WHERE id = :id")
    suspend fun deleteById(id: Long)
}
