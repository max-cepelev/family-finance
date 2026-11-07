package com.familyfinance.web

import com.familyfinance.domain.model.CategoryType
import com.familyfinance.domain.model.TransactionType
import com.familyfinance.ui.common.Dates
import com.familyfinance.web.WebPages.layout
import java.time.YearMonth
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.strong
import kotlinx.html.summary

/** Readable server-rendered reports; no chart libraries or network assets required. */
internal object WebReportPages {
    fun HTML.files(accounts: WebAccounts, month: YearMonth, device: String) = layout("Импорт и экспорт", "/files", month, device) {
        h1 { +"Импорт и экспорт" }
        section("card file-card") {
            h2 { +"Выгрузить операции в CSV" }
            p("hint") { +"Формат приложения: UTF-8 для Excel, разделитель — точка с запятой. Скрытые счета тоже доступны. Части разделённых операций — отдельные строки." }
            form(action = "/export.csv", method = FormMethod.get, classes = "export-form") {
                label("field") {
                    span("label") { +"Период" }
                    select { name = "period"; option { value = "MONTH"; +"Месяц" }; option { value = "YEAR"; +"Год" }; option { value = "ALL"; +"Всё время" } }
                }
                label("field") {
                    span("label") { +"Месяц / месяц выбранного года" }
                    input(type = InputType.month, name = "month") { value = month.toString(); min = "0001-01"; max = "9998-12"; required = true }
                }
                label("field") {
                    span("label") { +"Счёт" }
                    select {
                        name = "account"
                        option { value = ""; +"Все счета" }
                        accounts.groups.flatMap { it.rows }.forEach { row ->
                            option { value = row.uid; +(row.name + if (row.hidden) " (скрыт)" else "") }
                        }
                    }
                }
                button(classes = "primary") { type = ButtonType.submit; +"Скачать CSV" }
            }
        }
        section("card file-card") {
            h2 { +"Импорт на телефоне" }
            p { +"Импорт CSV, банковских выписок и резервной копии Money Manager пока выполняется в приложении: «Ещё» → «Импорт»." }
            p("hint") { +"Веб-загрузка с предварительной проверкой и сопоставлением счетов пока не реализована. Внешние сервисы не используются." }
        }
    }

    fun HTML.categories(page: WebCategories, month: YearMonth, device: String) = layout("Категории", "/categories", month, device) {
        h1 { +"Категории" }
        div("tabs") {
            listOf(CategoryType.EXPENSE to "Расходы", CategoryType.INCOME to "Доходы").forEach { (type, label) ->
                a("/categories?month=$month&type=$type", classes = if (type == page.type) "tab active" else "tab") { +label }
            }
        }
        p("hint") { +"Количество операций за всё время. Нажмите на категорию, чтобы открыть её операции выбранного месяца." }
        if (page.groups.isEmpty()) p("empty") { +"Нет категорий" }
        page.groups.forEach { group ->
            section("card catalog-group") {
                categoryRow(group.top, month, page.type, top = true)
                group.children.forEach { categoryRow(it, month, page.type) }
            }
        }
        if (page.archived.isNotEmpty()) details("card archived") {
            summary { +"Архив (${page.archived.size})" }
            page.archived.forEach { categoryRow(it, month, page.type) }
        }
        p("hint") { +"Добавление, изменение и объединение категорий пока доступны на телефоне." }
    }

    private fun FlowContent.categoryRow(row: WebCategoryRow, month: YearMonth, type: CategoryType, top: Boolean = false) =
        a(WebLinks.to("/ops", "month" to month.toString(), "category" to row.uid, "type" to type.name), classes = "catalog-row" + if (top) " catalog-top" else "") {
            span("avatar") { +row.icon }
            span("catalog-name") { +row.name }
            span("muted") { +Dates.operations(row.operations) }
        }

    fun HTML.accounts(page: WebAccounts, month: YearMonth, device: String) = layout("Счета", "/accounts", month, device) {
        h1 { +"Счета" }
        p("hint") { +"Остатки за всё время. Скрытые счета не входят в итоги. Нажмите на счёт, чтобы открыть операции выбранного месяца." }
        div("totals") {
            summary("Активы", page.assets, "income")
            summary("Долги", page.liabilities, "expense")
            summary("Итого", page.total)
        }
        if (page.groups.isEmpty()) p("empty") { +"Нет счетов. Добавьте первый счёт на телефоне." }
        page.groups.forEach { group ->
            section("card catalog-group") {
                div("catalog-head") { h2 { +group.name }; strong { +group.total } }
                group.rows.forEach { row ->
                    a(WebLinks.to("/ops", "month" to month.toString(), "account" to row.uid), classes = "catalog-row") {
                        span { +row.name; if (row.hidden) span("tag") { +"скрыт" } }
                        strong(if (row.negative) "expense" else "") { +row.balance }
                    }
                }
            }
        }
        p("hint") { +"Настройка счетов и кредитных карт пока доступна на телефоне." }
    }

    private fun FlowContent.summary(label: String, value: String, kind: String = "") = div("card total") {
        span("label") { +label }; span("value $kind") { +value }
    }

    fun HTML.statistics(page: WebStatistics, device: String) = layout("Статистика", "/stats", page.month, device) {
        h1 { +"Статистика" }
        monthToolbar(page.month) { month -> WebLinks.to("/stats", "month" to month.toString(), "type" to page.type.name, "parent" to page.parent?.uid) }
        div("tabs") {
            listOf(TransactionType.EXPENSE to "Расходы", TransactionType.INCOME to "Доходы").forEach { (type, label) ->
                a("/stats?month=${page.month}&type=$type", classes = if (type == page.type) "tab active" else "tab") { +label }
            }
        }
        page.parent?.let { parent ->
            a("/stats?month=${page.month}&type=${page.type}", classes = "link") { +"‹ Все категории" }
            h2 { +parent.name }
        }
        div("card report-summary") {
            span("muted") { +(if (page.type == TransactionType.INCOME) "Всего доходов" else "Всего расходов") }
            strong(if (page.type == TransactionType.INCOME) "income" else "expense") { +page.total }
            p("hint") { +"В основной валюте по сохранённым курсам. Переводы не учитываются." }
        }
        if (page.rows.isEmpty()) p("empty") { +"Нет операций за этот месяц" }
        section("card report-rows") {
            page.rows.forEach { row ->
                div("report-row") {
                    span("avatar") { attributes["style"] = "background:${row.color}22;color:${row.color}"; +row.icon }
                    div("report-name") {
                        if (row.canOpen) {
                            a(WebLinks.to("/stats", "month" to page.month.toString(), "type" to page.type.name, "parent" to row.uid)) { +"${row.name} ›" }
                        } else span { +row.name }
                        div("bar") { span { attributes["style"] = "width:${row.width}%;background:${row.color}" } }
                    }
                    span("muted share") { +row.share }
                    strong("amount") { +row.amount }
                    a(WebLinks.to("/ops", "month" to page.month.toString(), "type" to page.type.name,
                        "category" to (row.uid ?: ""), "only" to (page.parent != null).toString()), classes = "link") { +"Операции" }
                }
            }
        }
    }

    private fun FlowContent.monthToolbar(month: YearMonth, link: (YearMonth) -> String) = div("toolbar") {
        a(link(month.minusMonths(1)), classes = "arrow") { attributes["aria-label"] = "Предыдущий месяц"; +"‹" }
        h2 { +Dates.month(month) }
        a(link(month.plusMonths(1)), classes = "arrow") { attributes["aria-label"] = "Следующий месяц"; +"›" }
    }
}
