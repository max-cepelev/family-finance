package com.familyfinance.web

import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in browser fixture: loopback only, synthetic data, no phone or database access. */
class WebPreviewTest {
    @Test fun `serve synthetic web preview when explicitly enabled`() {
        assumeTrue(java.lang.Boolean.getBoolean("web.preview"))
        val store = WebTestStore()
        val finished = CompletableDeferred<Unit>()
        val sessions = object : WebSessions {
            override fun login(code: String, userAgent: String?): String? = null
            override fun check(token: String?) = true
        }
        val server = embeddedServer(CIO, host = "127.0.0.1", port = 18080) {
            webApp(sessions, store.data, store.edits, "Тестовые данные — не ваш бюджет")
            routing {
                post("/__preview/stop") {
                    call.respondText("Preview stopped")
                    finished.complete(Unit)
                }
            }
        }.start(wait = false)
        try {
            runBlocking { withTimeoutOrNull(600_000) { finished.await() } }
        } finally {
            server.stop(gracePeriodMillis = 200, timeoutMillis = 1_000)
        }
    }
}
