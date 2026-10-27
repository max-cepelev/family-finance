package com.familyfinance.web

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.familyfinance.MainActivity
import com.familyfinance.R
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import java.net.BindException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps the web server of "Доступ с компьютера" alive while the app is in the background. It shows a notification
 * the whole time and stops itself after [IDLE_LIMIT] without requests. While it runs, the phone keeps the processor
 * and Wi-Fi awake, so a locked phone off the charger still answers the browser at once.
 */
@AndroidEntryPoint
class WebAccessService : Service() {
    @Inject lateinit var access: WebAccess
    @Inject lateinit var data: RepositoryWebData
    @Inject lateinit var edits: RepositoryWebEdits

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: EmbeddedServer<*, *>? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        if (server == null) scope.launch { open() }
        return START_NOT_STICKY
    }

    private suspend fun open() {
        val device = listOfNotNull(Build.MODEL, WebAccess.wifiAddress()?.hostAddress).joinToString(" · ")
        for (port in WebAccess.PORT until WebAccess.PORT + PORTS_TO_TRY) {
            try {
                server = embeddedServer(CIO, port = port, host = "0.0.0.0") { webApp(access, data, edits, device) }.start(wait = false)
                access.onStarted(port)
                stayAwake()
                watchIdle()
                return
            } catch (e: BindException) {
                // Something else holds the port; try the next one.
            }
        }
        stopSelf()
    }

    private fun stayAwake() {
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "familyfinance:web")
            .apply { setReferenceCounted(false); acquire() }
        // Android 14 made this lock do nothing; there Wi-Fi power saving only adds a fraction of a second.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            wifiLock = applicationContext.getSystemService(WifiManager::class.java)
                .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "familyfinance:web")
                .apply { setReferenceCounted(false); acquire() }
        }
    }

    private suspend fun watchIdle() {
        while (true) {
            delay(60_000)
            if (System.currentTimeMillis() - access.lastActivity > IDLE_LIMIT) {
                stopSelf()
                return
            }
        }
    }

    override fun onDestroy() {
        server?.stop(gracePeriodMillis = 200, timeoutMillis = 1_000)
        server = null
        wakeLock?.release()
        wifiLock?.release()
        scope.cancel()
        access.onStopped()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Доступ с компьютера", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, WebAccessService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Доступ с компьютера включён")
            .setContentText("Выключится сам через 30 минут без действий")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Выключить", stop)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.familyfinance.web.STOP"
        private const val CHANNEL = "web_access"
        private const val NOTIFICATION_ID = 7
        private const val PORTS_TO_TRY = 10
        private const val IDLE_LIMIT = 30 * 60 * 1000L
    }
}
