package com.factory.timetilescountdownwidgets.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "countdown_events")
data class CountdownEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val emoji: String,
    val colorHex: String,
    val targetDateTime: Long,
    val repeatsYearly: Boolean,
    val lastNotifiedInstant: Long = 0L,
    val createdAt: Long
)
