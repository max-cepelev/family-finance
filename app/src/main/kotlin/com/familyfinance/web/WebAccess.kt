package com.familyfinance.web

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A browser that entered the code. */
data class WebClient(val token: String, val name: String, val lastSeen: Long)

data class WebAccessState(
    val running: Boolean = false,
    /** "192.168.1.23:8080" while running. */
    val address: String? = null,
    val code: String = "",
    val clients: List<WebClient> = emptyList(),
    /** Why it could not be switched on. */
    val error: String? = null,
)

/** What the web server asks of the access it runs under; a fake stands in for it in tests. */
interface WebSessions {
    /** A session token when [code] is the current one, null otherwise. */
    fun login(code: String, userAgent: String?): String?
    /** True for a token of a connected browser; counts as activity. */
    fun check(token: String?): Boolean
}

/**
 * "Доступ с компьютера": the address, the one-time code and the browsers let in. The server itself lives in
 * [WebAccessService]; this is what the phone screen shows and what the server checks requests against.
 */
@Singleton
class WebAccess @Inject constructor(@ApplicationContext private val context: Context) : WebSessions {
    private val _state = MutableStateFlow(WebAccessState())
    val state: StateFlow<WebAccessState> = _state.asStateFlow()

    private val sessions = ConcurrentHashMap<String, WebClient>()
    private val random = SecureRandom()
    @Volatile private var failedLogins = 0
    @Volatile var lastActivity: Long = 0L
        private set

    fun start() {
        if (_state.value.running) return
        if (wifiAddress() == null) {
            _state.update { it.copy(error = "Подключитесь к Wi-Fi: доступ работает только внутри домашней сети") }
            return
        }
        _state.update { it.copy(error = null) }
        ContextCompat.startForegroundService(context, Intent(context, WebAccessService::class.java))
    }

    fun stop() {
        context.startService(Intent(context, WebAccessService::class.java).setAction(WebAccessService.ACTION_STOP))
    }

    fun disconnect(token: String) {
        sessions.remove(token)
        publishClients()
    }

    /** Called by the service once the port is open. */
    internal fun onStarted(port: Int) {
        sessions.clear()
        failedLogins = 0
        lastActivity = System.currentTimeMillis()
        _state.value = WebAccessState(running = true, address = "${wifiAddress()?.hostAddress}:$port", code = newCode())
    }

    internal fun onStopped() {
        sessions.clear()
        _state.value = WebAccessState()
    }

    override fun login(code: String, userAgent: String?): String? {
        val current = _state.value
        if (!current.running) return null
        if (code.filter(Char::isDigit) != current.code) {
            // Guessing a six-digit code takes many tries; a new code after a few wrong ones makes it hopeless.
            if (++failedLogins >= MAX_FAILED_LOGINS) {
                failedLogins = 0
                _state.update { it.copy(code = newCode()) }
            }
            return null
        }
        failedLogins = 0
        val token = ByteArray(24).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        sessions[token] = WebClient(token, clientName(userAgent), System.currentTimeMillis())
        lastActivity = System.currentTimeMillis()
        publishClients()
        return token
    }

    override fun check(token: String?): Boolean {
        val client = token?.let(sessions::get) ?: return false
        val now = System.currentTimeMillis()
        sessions[client.token] = client.copy(lastSeen = now)
        lastActivity = now
        if (now - client.lastSeen > PUBLISH_EVERY) publishClients()
        return true
    }

    private fun publishClients() = _state.update { it.copy(clients = sessions.values.sortedByDescending { c -> c.lastSeen }) }

    private fun newCode(): String = (0 until 6).joinToString("") { random.nextInt(10).toString() }

    companion object {
        const val PORT = 8080
        private const val MAX_FAILED_LOGINS = 5
        private const val PUBLISH_EVERY = 30_000L

        /** The phone's address in the Wi-Fi it is on; null without Wi-Fi. */
        fun wifiAddress(): Inet4Address? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && (it.name.startsWith("wlan") || it.name.startsWith("swlan")) }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
        }.getOrNull()

        /** "Chrome · Windows" from a User-Agent. */
        fun clientName(userAgent: String?): String {
            val ua = userAgent.orEmpty()
            val browser = when {
                "Edg/" in ua -> "Edge"
                "YaBrowser" in ua -> "Яндекс Браузер"
                "OPR/" in ua -> "Opera"
                "Firefox/" in ua -> "Firefox"
                "Chrome/" in ua -> "Chrome"
                "Safari/" in ua -> "Safari"
                else -> "Браузер"
            }
            val system = when {
                "Windows" in ua -> "Windows"
                "Mac OS X" in ua -> "macOS"
                "Android" in ua -> "Android"
                "Linux" in ua -> "Linux"
                else -> null
            }
            return listOfNotNull(browser, system).joinToString(" · ")
        }
    }
}
