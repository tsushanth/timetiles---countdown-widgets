package com.factory.timetilescountdownwidgets.util

import com.factory.timetilescountdownwidgets.data.CountdownEvent
import java.util.Calendar

data class RemainingTime(
    val totalSeconds: Long,
    val days: Long,
    val hours: Long,
    val minutes: Long,
    val seconds: Long,
    val isPast: Boolean
)

fun effectiveTargetMillis(event: CountdownEvent, now: Long): Long {
    if (!event.repeatsYearly) return event.targetDateTime

    val target = Calendar.getInstance().apply { timeInMillis = event.targetDateTime }
    val current = Calendar.getInstance().apply { timeInMillis = now }

    val next = Calendar.getInstance().apply {
        clear()
        set(
            current.get(Calendar.YEAR),
            target.get(Calendar.MONTH),
            target.get(Calendar.DAY_OF_MONTH),
            target.get(Calendar.HOUR_OF_DAY),
            target.get(Calendar.MINUTE),
            target.get(Calendar.SECOND)
        )
    }
    if (next.timeInMillis < now) {
        next.set(Calendar.YEAR, current.get(Calendar.YEAR) + 1)
    }
    return next.timeInMillis
}

fun remainingTime(targetMillis: Long, now: Long): RemainingTime {
    val diff = targetMillis - now
    val isPast = diff <= 0
    val absDiff = kotlin.math.abs(diff)
    val totalSeconds = absDiff / 1000
    val days = totalSeconds / 86400
    val hours = (totalSeconds % 86400) / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return RemainingTime(totalSeconds, days, hours, minutes, seconds, isPast)
}
