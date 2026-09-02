package com.factory.timetilescountdownwidgets.data

import kotlinx.coroutines.flow.Flow

class CountdownRepository(private val dao: CountdownDao) {
    fun observeAll(): Flow<List<CountdownEvent>> = dao.observeAll()
    fun observeById(id: Long): Flow<CountdownEvent?> = dao.observeById(id)
    suspend fun getById(id: Long): CountdownEvent? = dao.getById(id)
    suspend fun getAllOnce(): List<CountdownEvent> = dao.getAllOnce()
    suspend fun insert(event: CountdownEvent): Long = dao.insert(event)
    suspend fun update(event: CountdownEvent) = dao.update(event)
    suspend fun delete(event: CountdownEvent) = dao.delete(event)
    suspend fun deleteById(id: Long) = dao.deleteById(id)
}
