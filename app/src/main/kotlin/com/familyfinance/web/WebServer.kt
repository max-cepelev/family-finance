package com.familyfinance.web

import com.familyfinance.domain.model.CategoryType
import com.familyfinance.domain.model.TransactionType
import com.familyfinance.web.WebPages.list
import com.familyfinance.web.WebPages.login
import com.familyfinance.web.WebPages.operations
import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.html.respondHtml
import io.ktor.server.request.header
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.util.toMap
import java.net.InetAddress
import java.time.YearMonth
import kotlinx.html.div
import kotlinx.html.stream.createHTML

/** Name of the session cookie. */
private const val SESSION = "ff_session"

/** Where htmx sits inside its WebJar. */
private const val HTMX = "META-INF/resources/webjars/htmx.org/2.0.4/dist/htmx.min.js"

/**
 * The pages of "Доступ с компьютера". [device] names the phone in the page footer.
 * Only browsers from the local network get an answer, and only after the code from the phone.
 */
fun Application.webApp(sessions: WebSessions, data: WebData, edits: WebEdits, device: String) {
    intercept(ApplicationCallPipeline.Plugins) {
        // Financial pages, htmx fragments and downloads must not survive in a shared browser's cache.
        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
        // The actual peer, not a header it could fake: the home network or this phone itself (adb, its own browser).
        val peer = runCatching { InetAddress.getByName(call.request.local.remoteAddress) }.getOrNull()
        if (peer == null || !(peer.isSiteLocalAddress || peer.isLoopbackAddress || peer.isLinkLocalAddress)) {
            call.respondText("Доступ только из домашней сети", status = HttpStatusCode.Forbidden)
            finish()
        }
    }
    routing {
        get("/static/htmx.min.js") { call.resource(HTMX, ContentType.Application.JavaScript) }
        get("/static/app.css") { call.resource("web/app.css", ContentType.Text.CSS) }
        get("/static/app.js") { call.resource("web/app.js", ContentType.Application.JavaScript) }

        get("/login") { call.respondHtml { login(error = false) } }
        post("/login") {
            val code = call.receiveParameters()["code"].orEmpty()
            val token = sessions.login(code, call.request.header(HttpHeaders.UserAgent))
            if (token == null) {
                call.respondHtml(HttpStatusCode.Unauthorized) { login(error = true) }
            } else {
                call.response.cookies.append(Cookie(SESSION, token, path = "/", httpOnly = true, extensions = mapOf("SameSite" to "Strict")))
                call.respondRedirect("/ops")
            }
        }

        get("/") { if (call.signedIn(sessions)) call.respondRedirect("/ops") }
        get("/ops") {
            if (!call.signedIn(sessions)) return@get
            val query = call.request.queryParameters["q"].orEmpty()
            val page = data.month(call.month(), query, call.filter())
            call.respondHtml { operations(page, query, device) }
        }
        // What the search box asks for: the totals and the day list.
        get("/ops/days") {
            if (!call.signedIn(sessions)) return@get
            val page = data.month(call.month(), call.request.queryParameters["q"].orEmpty(), call.filter())
            // The search box picks #list out of this (hx-select).
            call.respondText(createHTML().div { list(page) }, ContentType.Text.Html)
        }

        get("/stats") {
            if (!call.signedIn(sessions)) return@get
            val type = if (call.request.queryParameters["type"] == "INCOME")
                TransactionType.INCOME else TransactionType.EXPENSE
            val page = data.statistics(call.month(), type, call.request.queryParameters["parent"])
            call.respondHtml { with(WebReportPages) { statistics(page, device) } }
        }
        get("/accounts") {
            if (!call.signedIn(sessions)) return@get
            val page = data.accounts()
            call.respondHtml { with(WebReportPages) { accounts(page, call.month(), device) } }
        }
        get("/categories") {
            if (!call.signedIn(sessions)) return@get
            val type = if (call.request.queryParameters["type"] == "INCOME") CategoryType.INCOME else CategoryType.EXPENSE
            val page = data.categories(type)
            call.respondHtml { with(WebReportPages) { categories(page, call.month(), device) } }
        }
        get("/files") {
            if (!call.signedIn(sessions)) return@get
            val accounts = data.accounts()
            call.respondHtml { with(WebReportPages) { files(accounts, call.month(), device) } }
        }
        get("/export.csv") {
            if (!call.signedIn(sessions)) return@get
            val rawPeriod = call.request.queryParameters["period"] ?: "MONTH"
            val period = WebExportPeriod.entries.firstOrNull { it.name == rawPeriod }
            if (period == null) {
                call.respondText("Неизвестный период", status = HttpStatusCode.BadRequest)
                return@get
            }
            val file = data.export(call.month(), period, call.request.queryParameters["account"]?.ifEmpty { null })
            call.response.headers.append(HttpHeaders.ContentDisposition, "attachment; filename=\"${file.fileName}\"")
            call.respondText(file.text, ContentType.parse("text/csv; charset=UTF-8"))
        }

        // The edit panel. Each answer is the whole panel, swapped in for #panel.
        get("/ops/edit") {
            if (!call.signedIn(sessions)) return@get
            val form = edits.open(call.request.queryParameters["uid"]?.ifEmpty { null }, call.month())
            call.respondText(WebPanel.panel(form, focus = true), ContentType.Text.Html)
        }
        post("/ops/form") {
            if (!call.signedIn(sessions)) return@post
            val fields = call.receiveParameters()
            call.respondText(WebPanel.panel(edits.change(fields.toMap(), fields["action"])), ContentType.Text.Html)
        }
        post("/ops/save") {
            if (!call.signedIn(sessions)) return@post
            val form = edits.save(call.receiveParameters().toMap())
            if (form == null) call.closePanel() else call.respondText(WebPanel.panel(form), ContentType.Text.Html)
        }
        post("/ops/delete") {
            if (!call.signedIn(sessions)) return@post
            call.receiveParameters()["uid"]?.ifEmpty { null }?.let { edits.delete(it) }
            call.closePanel()
        }
    }
}

/** Closes the panel and tells the list to reload (it listens for `ops-changed`). */
private suspend fun ApplicationCall.closePanel() {
    response.headers.append("HX-Trigger", "ops-changed")
    respondText(WebPanel.closed(), ContentType.Text.Html)
}

/** False, with the browser sent to the code page, when there is no valid session. */
private suspend fun ApplicationCall.signedIn(sessions: WebSessions): Boolean {
    if (sessions.check(request.cookies[SESSION])) return true
    if (request.header("HX-Request") != null) {
        // htmx follows this header with a full page load.
        response.headers.append("HX-Redirect", "/login")
        respondText("", status = HttpStatusCode.Unauthorized)
    } else {
        respondRedirect("/login")
    }
    return false
}

private fun ApplicationCall.month(): YearMonth =
    request.queryParameters["month"]?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        ?.takeIf { it.year in 1..9998 } ?: YearMonth.now()

private fun ApplicationCall.filter(): WebFilter = WebFilter(
    account = request.queryParameters["account"]?.ifEmpty { null },
    category = request.queryParameters["category"],
    type = request.queryParameters["type"]?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() },
    only = request.queryParameters["only"] == "true",
)

private suspend fun ApplicationCall.resource(path: String, type: ContentType) {
    val bytes = WebPages::class.java.classLoader?.getResourceAsStream(path)?.use { it.readBytes() }
    if (bytes == null) respondText("Нет файла", status = HttpStatusCode.NotFound) else respondBytes(bytes, type)
}
