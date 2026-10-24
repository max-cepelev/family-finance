package com.familyfinance.data.device

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Who is writing: stamped on every change and names this device's log in the sync folder. */
interface DeviceIdentity {
    val deviceId: String
}

/** Stable per-install id. */
@Singleton
class DeviceIdStore @Inject constructor(@ApplicationContext context: Context) : DeviceIdentity {
    private val prefs = context.getSharedPreferences("device", Context.MODE_PRIVATE)

    override val deviceId: String by lazy {
        prefs.getString(KEY, null) ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY, it).apply() }
    }

    private companion object {
        const val KEY = "device_id"
    }
}
