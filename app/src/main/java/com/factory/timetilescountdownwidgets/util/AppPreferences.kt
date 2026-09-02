package com.factory.timetilescountdownwidgets.util

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appDataStore by preferencesDataStore(name = "app_prefs")

class AppPreferences(private val context: Context) {
    private val onboardingCompletedKey = booleanPreferencesKey("onboarding_completed")

    val onboardingCompleted: Flow<Boolean> = context.appDataStore.data
        .map { it[onboardingCompletedKey] ?: false }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.appDataStore.edit { it[onboardingCompletedKey] = completed }
    }
}
