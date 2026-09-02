package com.factory.timetilescountdownwidgets.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

fun Context.isOnline(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return true
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
