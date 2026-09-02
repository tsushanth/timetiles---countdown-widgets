package com.factory.timetilescountdownwidgets.util

import android.content.Context
import com.factory.timetilescountdownwidgets.TimeTilesApplication
import com.factory.timetilescountdownwidgets.billing.BillingManager
import com.factory.timetilescountdownwidgets.billing.PremiumManager
import com.factory.timetilescountdownwidgets.data.CountdownRepository

fun Context.countdownRepository(): CountdownRepository =
    (applicationContext as TimeTilesApplication).repository

fun Context.billingManager(): BillingManager =
    (applicationContext as TimeTilesApplication).billingManager

fun Context.premiumManager(): PremiumManager =
    (applicationContext as TimeTilesApplication).premiumManager
