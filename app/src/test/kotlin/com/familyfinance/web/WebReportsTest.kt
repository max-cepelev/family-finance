package com.familyfinance.web

import com.familyfinance.ui.common.MoneyFormatter
import com.familyfinance.domain.csv.AppCsv
import com.familyfinance.domain.csv.Csv
import com.familyfinance.domain.model.CategoryType
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class WebReportsTest {
    @Test fun `archived parent usage includes visible and archived children in its history`() {
        val store = WebTestStore()
        val categories = store.categoryList.map { if (it.uid in setOf("food", "groceries")) it.copy(archived = true) else it }
        val catalog = WebCategories.build(CategoryType.EXPENSE, categories, mapOf("groceries" to 1, "cafe" to 1))
        val food = catalog.archived.single { it.uid == "food" }
        assertEquals(2, food.operations)
        assertEquals(food.operations, WebFilter(category = "food").apply(store.items, categories).size)
        assertEquals(1, catalog.groups.single { it.top.uid == "cafe" }.top.operations)
    }

    @Test fun `parent category usage includes archived children just like its operations filter`() {
        val store = WebTestStore()
        val categories = store.categoryList.map { if (it.uid == "groceries") it.copy(archived = true) else it }
        val catalog = WebCategories.build(CategoryType.EXPENSE, categories, mapOf("groceries" to 1, "cafe" to 1))
        val food = catalog.groups.single { it.top.uid == "food" }
        assertEquals(2, food.top.operations)
        assertEquals(listOf("cafe"), food.children.map { it.uid })
        assertEquals(listOf("groceries"), catalog.archived.map { it.uid })
        assertEquals(food.top.operations, WebFilter(category = "food").apply(store.items, categories).size)
    }

    @Test fun `web export keeps imported negative expenses as refunds`() = testApplication {
        val store = WebTestStore()
        val original = store.items.first { it.uid == "shop-tx" }
        store.items += original.copy(uid = "refund", amount = -1250, accountAmount = -1250, note = "Возврат скидки")
        application { webApp(store.sessions, store.data, store.edits, "Тестовые данные") }
        val file = client.get("/export.csv?month=2026-10") { header(HttpHeaders.Cookie, "ff_session=web-test") }.bodyAsText()
        val refund = AppCsv.parse(Csv.parse(file)).first.single { it.note == "Возврат скидки" }
        assertEquals(original.type, refund.type)
        assertEquals(-1250L, refund.amount)
        assertTrue(file.contains("возврат расхода;-12,50"))
    }

    @Test fun `out of range month values do not crash reports or exports`() = testApplication {
        val store = WebTestStore()
        application { webApp(store.sessions, store.data, store.edits, "Тестовые данные") }
        for (month in listOf("not-a-month", "%2B999999999-12", "-999999999-01")) {
            for (path in listOf("/stats", "/ops", "/export.csv")) {
                val result = runCatching { client.get("$path?month=$month") { header(HttpHeaders.Cookie, "ff_session=web-test") } }
                assertEquals("$path?month=$month: ${result.exceptionOrNull()}", HttpStatusCode.OK, result.getOrNull()?.status)
            }
        }
    }

    @Test fun `monthly and yearly downloads use local calendar boundaries`() = testApplication {
        val store = WebTestStore()
        fun at(day: String) = LocalDateTime.parse(day).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val base = store.items.first()
        store.items += base.copy(uid = "previous", date = at("2025-12-31T23:59:59"), note = "Previous year")
        store.items += base.copy(uid = "january", date = at("2026-01-01T00:00:00"), note = "January")
        store.items += base.copy(uid = "next", date = at("2027-01-01T00:00:00"), note = "Next year")
        application { webApp(store.sessions, store.data, store.edits, "Тестовые данные") }
        val year = client.get("/export.csv?month=2026-10&period=YEAR") { header(HttpHeaders.Cookie, "ff_session=web-test") }
        assertTrue(year.headers[HttpHeaders.ContentDisposition].orEmpty().contains("budget-2026.csv"))
        val yearNotes = AppCsv.parse(Csv.parse(year.bodyAsText())).first.map { it.note }
        assertTrue("January" in yearNotes)
        assertFalse("Previous year" in yearNotes)
        assertFalse("Next year" in yearNotes)
        val month = client.get("/export.csv?month=2026-10&period=MONTH") { header(HttpHeaders.Cookie, "ff_session=web-test") }
        val monthNotes = AppCsv.parse(Csv.parse(month.bodyAsText())).first.map { it.note }
        assertFalse("January" in monthNotes)
        assertTrue("Магазин; у дома" in monthNotes)
    }

    @Test fun `reports require a session and financial downloads cannot be cached`() = testApplication {
        val store = WebTestStore()
        application { webApp(store.sessions, store.data, store.edits, "Тестовые данные") }
        val browser = createClient { followRedirects = false }
        for (path in listOf("/stats", "/accounts", "/categories", "/files", "/export.csv")) {
            val anonymous = browser.get(path)
            assertEquals(path, HttpStatusCode.Found, anonymous.status)
            assertEquals("/login", anonymous.headers[HttpHeaders.Location])
            val signed = browser.get("$path?month=2026-10") { header(HttpHeaders.Cookie, "ff_session=web-test") }
            assertEquals(path, HttpStatusCode.OK, signed.status)
            assertEquals(path, "no-store", signed.headers[HttpHeaders.CacheControl])
        }
    }

    @Test fun `csv download uses the app format and includes incoming transfers of the selected account`() = testApplication {
        val store = WebTestStore()
        application { webApp(store.sessions, store.data, store.edits, "Тестовые данные") }
        val files = client.get("/files?month=2026-10") { header(HttpHeaders.Cookie, "ff_session=web-test") }
        assertEquals(HttpStatusCode.OK, files.status)
        assertTrue(files.bodyAsText().contains("/export.csv"))
        val download = client.get("/export.csv?month=2026-10&period=MONTH&account=cash") { header(HttpHeaders.Cookie, "ff_session=web-test") }
        assertEquals(HttpStatusCode.OK, download.status)
        assertTrue(download.headers[HttpHeaders.ContentDisposition].orEmpty().contains("budget-2026-10.csv"))
        assertTrue(download.headers[HttpHeaders.ContentType].orEmpty().startsWith("text/csv"))
        val text = download.bodyAsText()
        assertTrue(text.startsWith("\uFEFF"))
        val (ops, errors) = AppCsv.parse(Csv.parse(text))
        assertTrue(errors.isEmpty())
        assertEquals(setOf("Кофе", "Снятие наличных"), ops.map { it.note }.toSet())
        val all = client.get("/export.csv?month=2026-10&period=ALL") { header(HttpHeaders.Cookie, "ff_session=web-test") }
        val allOps = AppCsv.parse(Csv.parse(all.bodyAsText())).first
        assertEquals(store.items.size, allOps.size)
        assertTrue(allOps.any { it.note == "Магазин; у дома" })
        val wrong = client.get("/export.csv?period=INVALID") { header(HttpHeaders.Cookie, "ff_session=web-test") }
        assertEquals(HttpStatusCode.BadRequest, wrong.status)
    }

    @Test fun `category catalog links to exact category operations and keeps filters during search`() = testApplication {
        val store = WebTestStore()
        store.categoryList += store.categoryList.first().copy(uid = "old", name = "Старая категория", archived = true)
        application { webApp(store.sessions, store.data, store.edits, "Тестовые данные") }
        val page = client.get("/categories?month=2026-10") { header(HttpHeaders.Cookie, "ff_session=web-test") }
        assertEquals(HttpStatusCode.OK, page.status)
        val html = page.bodyAsText()
        assertTrue(html.contains("Питание"))
        assertTrue(html.contains("Продукты"))
        assertTrue(html.contains("Старая категория"))
        assertTrue(html.contains("category=food"))
        assertFalse(html.contains("category=salary"))
        val filtered = client.get("/ops?month=2026-10&category=groceries&type=EXPENSE") { header(HttpHeaders.Cookie, "ff_session=web-test") }.bodyAsText()
        assertTrue(filtered.contains("Магазин; у дома"))
        assertFalse(filtered.contains("Кофе"))
        assertFalse(filtered.contains("Снятие наличных"))
        assertTrue(filtered.contains("/ops/days?month=2026-10&amp;category=groceries&amp;type=EXPENSE"))
        assertTrue(filtered.contains("/ops?month=2026-09&amp;category=groceries&amp;type=EXPENSE"))
        val searched = client.get("/ops/days?month=2026-10&category=groceries&q=Кофе") { header(HttpHeaders.Cookie, "ff_session=web-test") }.bodyAsText()
        assertTrue(searched.contains("Нет операций"))
        val parent = client.get("/ops?month=2026-10&category=food") { header(HttpHeaders.Cookie, "ff_session=web-test") }.bodyAsText()
        assertTrue(parent.contains("Магазин; у дома"))
        assertTrue(parent.contains("Кофе"))
        val only = client.get("/ops?month=2026-10&category=food&only=true") { header(HttpHeaders.Cookie, "ff_session=web-test") }.bodyAsText()
        assertTrue(only.contains("Нет операций"))
    }

    @Test fun `accounts show lifetime balances and open either side of transfers`() = testApplication {
        val store = WebTestStore()
        application { webApp(store.sessions, store.data, store.edits, "Тестовые данные") }
        val page = client.get("/accounts?month=2026-10") { header(HttpHeaders.Cookie, "ff_session=web-test") }
        assertEquals(HttpStatusCode.OK, page.status)
        val html = page.bodyAsText()
        assertTrue(html.contains("Банки"))
        assertTrue(html.contains("Без группы"))
        assertTrue(html.contains(MoneyFormatter.format(100000 - 12543 - 2500, "RUB")))
        assertTrue(html.contains("Скрытый счёт"))
        assertTrue(html.contains("account=cash"))
        val cash = client.get("/ops?month=2026-10&account=cash") { header(HttpHeaders.Cookie, "ff_session=web-test") }.bodyAsText()
        assertTrue(cash.contains("Кофе"))
        assertTrue(cash.contains("Снятие наличных"))
        assertFalse(cash.contains("Зарплата за октябрь"))
        assertTrue(cash.contains("account=cash"))
        assertTrue(cash.contains("Сбросить фильтр"))
    }

    @Test fun `statistics aggregate categories and drill down without counting transfers`() = testApplication {
        val store = WebTestStore()
        application { webApp(store.sessions, store.data, store.edits, "Тестовые данные") }
        val page = client.get("/stats?month=2026-10") { header(HttpHeaders.Cookie, "ff_session=web-test") }
        assertEquals(HttpStatusCode.OK, page.status)
        val html = page.bodyAsText()
        assertTrue(html.contains("Статистика"))
        assertTrue(html.contains("Питание"))
        assertTrue(html.contains(MoneyFormatter.format(15043, "RUB")))
        assertTrue(html.contains("parent=food"))
        assertFalse(html.contains("Снятие наличных"))
        val children = client.get("/stats?month=2026-10&parent=food") { header(HttpHeaders.Cookie, "ff_session=web-test") }.bodyAsText()
        assertTrue(children.contains("Продукты"))
        assertTrue(children.contains("Кафе"))
        assertTrue(children.contains("category=groceries"))
        val income = client.get("/stats?month=2026-10&type=INCOME") { header(HttpHeaders.Cookie, "ff_session=web-test") }.bodyAsText()
        assertTrue(income.contains("Зарплата"))
        assertFalse(income.contains("parent=food"))
    }
}
