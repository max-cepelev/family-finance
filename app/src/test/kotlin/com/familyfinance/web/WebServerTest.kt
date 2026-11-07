package com.familyfinance.web

import com.familyfinance.domain.model.Account
import com.familyfinance.domain.model.Category
import com.familyfinance.domain.model.CategoryType
import com.familyfinance.domain.model.MainCurrency
import com.familyfinance.domain.model.Transaction
import com.familyfinance.domain.model.TransactionType
import com.familyfinance.ui.common.MoneyFormatter
import com.familyfinance.ui.transaction.DraftError
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.testing.testApplication
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebServerTest {
    private class FakeSessions : WebSessions {
        val tokens = mutableSetOf<String>()
        override fun login(code: String, userAgent: String?): String? = if (code == "482913") "t1".also { tokens += it } else null
        override fun check(token: String?): Boolean = token in tokens
    }

    private val data = object : WebData by WebTestStore().data {
        override suspend fun month(month: YearMonth, query: String, filter: WebFilter): WebMonth {
            val rows = listOf(
                WebRow("t-1", "🛒", "Питание › Продукты", "Лента", listOf("чек"), "Сбербанк", "1 254,30 ₽", TransactionType.EXPENSE),
                WebRow("t-2", "☕", "Питание › Кафе", "Шоколадница", emptyList(), "Т-Банк", "68,00 ₽", TransactionType.EXPENSE),
            ).filter { query.isEmpty() || query.lowercase() in (it.note + it.category).lowercase() }
            return WebMonth(month, "Октябрь 2026", "0,00 ₽", "1 322,30 ₽", "-1 322,30 ₽", listOf(WebDay(26, "понедельник", "1 322,30 ₽", true, rows)))
        }
    }

    /** Remembers what was saved; an empty amount is the one error it knows. */
    private class FakeEdits : WebEdits {
        val saved = mutableListOf<WebFields>()
        val deleted = mutableListOf<String>()
        private fun form(uid: String?, errors: Set<DraftError> = emptySet()) = WebForm(
            uid = uid, month = YearMonth.of(2026, 10), type = TransactionType.EXPENSE, amount = "1254,3", currency = "₽",
            date = "2026-10-26", time = "14:42", account = "a", toAccount = "", toAmount = null, toCurrency = "",
            category = "", note = "Лента", accounts = listOf(WebOption("a", "Сбербанк")), categories = listOf(WebOption("", "Без категории")),
            parts = emptyList(), remainder = "", receipt = null, errors = errors,
        )
        override suspend fun open(uid: String?, month: YearMonth) = form(uid)
        override suspend fun change(fields: WebFields, action: String?) = form(fields["uid"]?.firstOrNull())
        override suspend fun save(fields: WebFields): WebForm? =
            if (fields["amount"]?.firstOrNull().isNullOrEmpty()) form(null, setOf(DraftError.AMOUNT)) else null.also { saved += fields }
        override suspend fun delete(uid: String) { deleted += uid }
    }

    @Test fun `pages need the code from the phone`() = testApplication {
        val sessions = FakeSessions()
        application { webApp(sessions, data, FakeEdits(), "Galaxy · 192.168.1.23") }
        val client = createClient { followRedirects = false }

        val anonymous = client.get("/ops")
        assertEquals(HttpStatusCode.Found, anonymous.status)
        assertEquals("/login", anonymous.headers[HttpHeaders.Location])
        // htmx gets told to load the code page instead of a redirect it would swap into the list.
        assertEquals("/login", client.get("/ops/days") { header("HX-Request", "true") }.headers["HX-Redirect"])

        val wrong = client.submitForm("/login", parameters { append("code", "000000") })
        assertEquals(HttpStatusCode.Unauthorized, wrong.status)
        assertTrue(wrong.bodyAsText().contains("Код не подошёл"))

        val right = client.submitForm("/login", parameters { append("code", "482913") })
        assertEquals(HttpStatusCode.Found, right.status)
        val cookie = right.headers.getAll(HttpHeaders.SetCookie).orEmpty().single()
        assertTrue(cookie.startsWith("ff_session=t1"))
        assertTrue("HttpOnly" in cookie)
    }

    @Test fun `operations page and search`() = testApplication {
        val sessions = FakeSessions().apply { tokens += "t1" }
        application { webApp(sessions, data, FakeEdits(), "Galaxy · 192.168.1.23") }

        val page = client.get("/ops?month=2026-10") { header(HttpHeaders.Cookie, "ff_session=t1") }.bodyAsText()
        assertTrue(page.contains("Октябрь 2026"))
        assertTrue(page.contains("Лента"))
        assertTrue(page.contains("hx-get=\"/ops/days?month=2026-10\""))
        assertTrue(page.contains("/ops?month=2026-09"))

        val found = client.get("/ops/days?month=2026-10&q=шоко") { header(HttpHeaders.Cookie, "ff_session=t1") }.bodyAsText()
        assertTrue(found.contains("id=\"list\""))
        assertTrue(found.contains("Шоколадница"))
        assertFalse(found.contains("Лента"))
    }

    @Test fun `edit panel saves and tells the list to reload`() = testApplication {
        val sessions = FakeSessions().apply { tokens += "t1" }
        val edits = FakeEdits()
        application { webApp(sessions, data, edits, "") }
        val cookie = "ff_session=t1"

        val page = client.get("/ops?month=2026-10") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        assertTrue(page.contains("hx-get=\"/ops/edit?uid=t-1&amp;month=2026-10\""))
        assertTrue(page.contains("hx-trigger=\"ops-changed from:body\""))
        // Rows sit inside the list; inheriting its hx-select would swap nothing in and lose the panel slot.
        assertTrue(page.contains("hx-disinherit=\"*\""))

        val panel = client.get("/ops/edit?uid=t-1&month=2026-10") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        assertTrue(panel.startsWith("<aside class=\"panel\" id=\"panel\">"))
        assertTrue(panel.contains("value=\"Лента\""))

        val invalid = client.submitForm("/ops/save", parameters { append("uid", "t-1"); append("amount", "") }) { header(HttpHeaders.Cookie, cookie) }
        assertTrue(invalid.bodyAsText().contains("Введите сумму"))
        assertNull(invalid.headers["HX-Trigger"])

        val saved = client.submitForm("/ops/save", parameters { append("uid", "t-1"); append("amount", "45") }) { header(HttpHeaders.Cookie, cookie) }
        assertEquals("ops-changed", saved.headers["HX-Trigger"])
        assertEquals("<aside id=\"panel\"></aside>", saved.bodyAsText().trim())
        assertEquals(listOf("45"), edits.saved.single()["amount"])

        val deleted = client.submitForm("/ops/delete", parameters { append("uid", "t-1") }) { header(HttpHeaders.Cookie, cookie) }
        assertEquals("ops-changed", deleted.headers["HX-Trigger"])
        assertEquals(listOf("t-1"), edits.deleted)

        // Without the code nothing is written.
        client.submitForm("/ops/delete", parameters { append("uid", "t-2") })
        assertEquals(listOf("t-1"), edits.deleted)
    }

    @Test fun `search totals cover only the rows found`() {
        val zone = ZoneId.of("Europe/Moscow")
        val day = LocalDate.of(2021, 3, 31).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        fun tx(uid: String, category: String, amount: Long, note: String) = Transaction(
            uid = uid, type = TransactionType.EXPENSE, date = day, amount = amount, currencyCode = "RUB",
            accountUid = "a", accountAmount = amount, categoryUid = category, note = note, updatedAt = 0, deviceId = "d",
        )
        fun category(uid: String, name: String) = Category(uid, null, name, CategoryType.EXPENSE, 0, updatedAt = 0, deviceId = "d")
        val items = listOf(tx("1", "med", 44000, "Нуксен"), tx("2", "food", 39990, "Продукты"), tx("3", "car", 60500, "Ремонт колеса"))
        fun build(query: String) = WebMonths.build(
            month = YearMonth.of(2021, 3),
            items = items,
            main = MainCurrency("RUB", emptyMap()),
            categories = listOf(category("med", "Лекарства"), category("food", "Питание"), category("car", "Транспорт")),
            accounts = listOf(Account("a", null, "ВТБ", "RUB", 0, updatedAt = 0, deviceId = "d")),
            query = query,
            zone = zone,
        )
        val money = { minor: Long -> MoneyFormatter.format(minor, "RUB") }

        val all = build("")
        assertEquals(money(144490), all.expense)
        assertEquals(money(144490), all.days.single().total)

        val found = build("лекарства")
        assertEquals(listOf("Нуксен"), found.days.single().rows.map { it.note })
        assertEquals(money(44000), found.days.single().total)
        assertEquals(money(44000), found.expense)
    }

    @Test fun `script and styles ship inside the app`() = testApplication {
        application { webApp(FakeSessions(), data, FakeEdits(), "") }
        assertTrue(client.get("/static/htmx.min.js").bodyAsText().contains("htmx"))
        assertTrue(client.get("/static/app.css").bodyAsText().contains("--primary"))
        assertTrue(client.get("/static/app.js").bodyAsText().contains("closePanel"))
    }

    @Test fun `browser names from user agents`() {
        assertEquals("Chrome · Windows", WebAccess.clientName("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"))
        assertEquals("Safari · macOS", WebAccess.clientName("Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15"))
        assertEquals("Edge · Windows", WebAccess.clientName("Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 Chrome/130.0 Safari/537.36 Edg/130.0"))
    }
}
