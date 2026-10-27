package com.familyfinance.data.push

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** "Добавлять операции из пушей" and whether Android lets the app read notifications at all. */
@Singleton
class PushSettings @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs = context.getSharedPreferences("push", Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /**
     * When reading was last switched on, epoch millis; 0 when not known (switched on before this was kept).
     * Pushes from before it came while the user did not want them, so catching up leaves them alone.
     */
    val enabledSince: Long get() = prefs.getLong(KEY_ENABLED_SINCE, 0L)

    fun setEnabled(value: Boolean) {
        val edit = prefs.edit().putBoolean(KEY_ENABLED, value)
        if (value && !_enabled.value) edit.putLong(KEY_ENABLED_SINCE, System.currentTimeMillis())
        edit.apply()
        _enabled.value = value
    }

    /** "Доступ к уведомлениям" granted in system settings. */
    fun hasAccess(): Boolean = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_ENABLED_SINCE = "enabled_since"
    }
}
