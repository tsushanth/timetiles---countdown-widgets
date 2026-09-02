package com.factory.timetilescountdownwidgets.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.factory.timetilescountdownwidgets.util.countdownRepository
import com.factory.timetilescountdownwidgets.util.effectiveTargetMillis

class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = applicationContext.countdownRepository()
        val now = System.currentTimeMillis()

        repository.getAllOnce().forEach { event ->
            val target = effectiveTargetMillis(event, now)
            if (target <= now && event.lastNotifiedInstant != target) {
                NotificationHelper.showArrivedNotification(applicationContext, event)
                repository.update(event.copy(lastNotifiedInstant = target))
            }
        }

        return Result.success()
    }
}
