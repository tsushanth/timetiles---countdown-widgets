package com.factory.timetilescountdownwidgets

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.factory.timetilescountdownwidgets.billing.BillingManager
import com.factory.timetilescountdownwidgets.billing.PremiumManager
import com.factory.timetilescountdownwidgets.data.CountdownDatabase
import com.factory.timetilescountdownwidgets.data.CountdownRepository
import com.factory.timetilescountdownwidgets.notification.NotificationHelper
import com.factory.timetilescountdownwidgets.notification.ReminderWorker
import com.factory.timetilescountdownwidgets.widget.WidgetUpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.TimeUnit

class TimeTilesApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repository: CountdownRepository by lazy {
        CountdownRepository(CountdownDatabase.getInstance(this).countdownDao())
    }

    val billingManager: BillingManager by lazy { BillingManager(this) }

    val premiumManager: PremiumManager by lazy { PremiumManager(this, billingManager, applicationScope) }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        schedulePeriodicWork()
        billingManager.startConnection()
    }

    private fun schedulePeriodicWork() {
        val workManager = WorkManager.getInstance(this)

        val reminderRequest = PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES).build()
        workManager.enqueueUniquePeriodicWork(
            "reminder_check",
            ExistingPeriodicWorkPolicy.KEEP,
            reminderRequest
        )

        val widgetRequest = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(15, TimeUnit.MINUTES).build()
        workManager.enqueueUniquePeriodicWork(
            "widget_refresh",
            ExistingPeriodicWorkPolicy.KEEP,
            widgetRequest
        )
    }
}
