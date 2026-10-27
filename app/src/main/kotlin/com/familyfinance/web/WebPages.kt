package com.familyfinance.web

import com.familyfinance.domain.model.TransactionType
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.aside
import kotlinx.html.body
import kotlinx.html.br
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.lang
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.nav
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.title

/** Pages of "Доступ с компьютера", built in Kotlin; htmx swaps parts of them without reloading. */
object WebPages {

    private fun HTML.page(title: String, content: FlowContent.() -> Unit) {
        lang = "ru"
        head {
            meta(charset = "utf-8")
            meta(name = "viewport", content = "width=device-width, initial-scale=1")
            title(title)
            link(rel = "stylesheet", href = "/static/app.css")
            script(src = "/static/htmx.min.js") {}
            script(src = "/static/app.js") {}
        }
        body { content() }
    }

    fun HTML.login(error: Boolean) = page("Вход — Семейный бюджет") {
        div("center") {
            section("card login") {
                div("brand") { span("logo") {}; span { +"Семейный бюджет" } }
                p("muted") { +"Введите код с экрана телефона: «Ещё» → «Доступ с компьютера»." }
                form(action = "/login", method = kotlinx.html.FormMethod.post) {
                    input(type = InputType.text, name = "code", classes = "code") {
                        attributes["inputmode"] = "numeric"
                        attributes["autocomplete"] = "one-time-code"
                        attributes["maxlength"] = "6"
                        attributes["autofocus"] = "autofocus"
                        attributes["aria-label"] = "Код"
                        placeholder = "••••••"
                    }
                    if (error) p("error") { +"Код не подошёл. Проверьте его на телефоне — после нескольких ошибок он меняется." }
                    button(classes = "primary") { +"Войти" }
                }
            }
        }
    }

    fun HTML.operations(month: WebMonth, query: String, device: String) = page("${month.title} — Семейный бюджет") {
        div("app") {
            aside("sidebar") {
                div("brand") { span("logo") {}; span { +"Семейный бюджет" } }
                nav {
                    a("/ops", classes = "nav active") { +"Операции" }
                    // The other sections come in the next steps; shown so the layout is the final one.
                    listOf("Статистика", "Счета", "Категории", "Импорт и экспорт").forEach {
                        span("nav soon") { attributes["title"] = "Скоро"; +it }
                    }
                }
                div("device") { +"Данные на телефоне"; br(); +device }
            }
            main("content") {
                div("toolbar") {
                    a("/ops?month=${month.month.minusMonths(1)}", classes = "arrow") { attributes["aria-label"] = "Предыдущий месяц"; +"‹" }
                    h1 { +month.title }
                    a("/ops?month=${month.month.plusMonths(1)}", classes = "arrow") { attributes["aria-label"] = "Следующий месяц"; +"›" }
                    span("spacer") {}
                    input(type = InputType.search, name = "q", classes = "search") {
                        attributes["aria-label"] = "Поиск"
                        placeholder = "Поиск по заметке и категории"
                        value = query
                        attributes["hx-get"] = "/ops/days?month=${month.month}"
                        attributes["hx-trigger"] = "input changed delay:300ms, search"
                        attributes["hx-target"] = "#list"
                        attributes["hx-select"] = "#list"
                        attributes["hx-swap"] = "outerHTML"
                    }
                    button(classes = "primary small") {
                        type = ButtonType.button
                        attributes["hx-get"] = "/ops/edit?month=${month.month}"
                        attributes["hx-target"] = "#panel"
                        attributes["hx-swap"] = "outerHTML"
                        +"+ Операция"
                    }
                }
                list(month)
            }
            closedPanel()
        }
    }

    /** Where the edit panel opens; empty and hidden until a row or "+ Операция" is clicked. */
    fun FlowContent.closedPanel() = aside { id = "panel" }

    private fun FlowContent.total(label: String, value: String, kind: String) =
        div("card total") { span("label") { +label }; span("value $kind") { +value } }

    /** Totals and the day list; also what the search box swaps in. */
    fun FlowContent.list(month: WebMonth) {
        div {
            id = "list"
            // Reloads itself, with the search applied, after an operation is saved or deleted.
            attributes["hx-get"] = "/ops/days?month=${month.month}"
            attributes["hx-trigger"] = "ops-changed from:body"
            attributes["hx-include"] = ".search"
            attributes["hx-select"] = "#list"
            attributes["hx-swap"] = "outerHTML"
            // Rows open the panel; they must not pick up these (htmx passes attributes down to children).
            attributes["hx-disinherit"] = "*"
            div("totals") {
                total("Доходы", month.income, "income")
                total("Расходы", month.expense, "expense")
                total("Итого", month.net, "")
            }
            days(month)
        }
    }

    private fun FlowContent.days(month: WebMonth) {
        div("days") {
            if (month.days.isEmpty()) p("empty") { +"Нет операций" }
            month.days.forEach { day ->
                section("card day") {
                    div("day-head") {
                        span("num") { +day.day.toString() }
                        span("weekday") { +day.weekday }
                        span(if (day.negative) "expense" else "income") { +day.total }
                    }
                    day.rows.forEach { row ->
                        div("row") {
                            attributes["data-uid"] = row.uid
                            attributes["hx-get"] = "/ops/edit?uid=${row.uid}&month=${month.month}"
                            attributes["hx-target"] = "#panel"
                            attributes["hx-swap"] = "outerHTML"
                            span("avatar") { +row.icon }
                            span("category") { +row.category }
                            span("note") {
                                +row.note
                                row.tags.forEach { span("tag") { +it } }
                            }
                            span("account") { +row.account }
                            span(
                                "amount " + when (row.type) {
                                    TransactionType.INCOME -> "income"
                                    TransactionType.EXPENSE -> "expense"
                                    TransactionType.TRANSFER -> ""
                                },
                            ) { +row.amount }
                        }
                    }
                }
            }
        }
    }
}
