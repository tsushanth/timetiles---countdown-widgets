package com.factory.timetilescountdownwidgets

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.factory.timetilescountdownwidgets.navigation.TimeTilesNavHost
import com.factory.timetilescountdownwidgets.ui.theme.TimeTilesTheme
import com.factory.timetilescountdownwidgets.util.billingManager

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()

        setContent {
            TimeTilesTheme {
                TimeTilesNavHost()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-syncs entitlements so an expired subscription (or a purchase completed
        // outside the app, e.g. via Play Store) is reflected without restarting the app.
        billingManager().refreshPurchasesIfReady()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
