package com.familyfinance.push

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.familyfinance.BuildConfig
import com.familyfinance.data.push.PushImporter
import com.familyfinance.data.push.PushSettings
import com.familyfinance.domain.bank.Bank
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Reads notifications of the supported banks (with "Доступ к уведомлениям") and records operations. */
@AndroidEntryPoint
class BankNotificationListener : NotificationListenerService() {
    @Inject lateinit var importer: PushImporter
    @Inject lateinit var settings: PushSettings

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) = read(sbn, missed = false)

    /**
     * Pushes that came while the listener was off (a reboot, an update, a force stop, a sleeping app) are still in
     * the shade unless swiped away; read them now. Ones recorded before are skipped as repeats, and ones from before
     * reading was switched on are left alone.
     */
    override fun onListenerConnected() {
        val shade = runCatching { activeNotifications }.getOrNull() ?: return
        shade.forEach { read(it, missed = true) }
    }

    private fun read(sbn: StatusBarNotification, missed: Boolean) {
        if (!settings.enabled.value) return
        // Left in the shade from while reading was off: the user did not want those.
        if (missed && sbn.postTime < settings.enabledSince) return
        val notification = sbn.notification ?: return
        // "У вас 2 уведомления от …" group summaries carry no operation.
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = notification.extras ?: return
        var title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()

        var bank = Bank.byPackage(sbn.packageName)
        if (bank == null && BuildConfig.DEBUG && sbn.packageName == SHELL) {
            // Debug builds only: `adb shell cmd notification post` with a title like "[sber] ➖ 10 ₽ …".
            bank = title?.let { t -> Bank.entries.firstOrNull { t.startsWith("[${it.code}]") } }
            title = title?.substringAfter("] ")
        }
        bank ?: return
        scope.launch {
            if (missed) importer.handleMissed(bank, title, text, sbn.postTime) else importer.handle(bank, title, text, sbn.postTime)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val SHELL = "com.android.shell"
    }
}
