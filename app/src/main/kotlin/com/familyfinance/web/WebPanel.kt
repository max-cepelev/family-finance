package com.familyfinance.web

import com.familyfinance.domain.model.TransactionType
import com.familyfinance.ui.transaction.DraftError
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.aside
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.stream.createHTML

/**
 * The edit panel beside the list. The form posts to the server and comes back redrawn: on a type change, a split,
 * a changed account, or with errors. Saving closes it and the list reloads itself.
 */
object WebPanel {

    /** The panel as htmx swaps it in for `#panel`; [focus] puts the cursor in the amount, for a panel just opened. */
    fun panel(form: WebForm, focus: Boolean = false): String = createHTML().aside("panel") {
        id = "panel"
        body(form, focus)
    }

    /** What closes the panel: the empty slot it lives in. */
    fun closed(): String = createHTML().aside { id = "panel" }

    private fun FlowContent.body(form: WebForm, focus: Boolean) {
        form(classes = "edit") {
            attributes["hx-post"] = "/ops/save"
            attributes["hx-target"] = "#panel"
            attributes["hx-swap"] = "outerHTML"
            hidden("uid", form.uid.orEmpty())
            hidden("month", form.month.toString())
            hidden("type", form.type.name)

            div("panel-head") {
                span("title") { +(if (form.uid == null) "Новая операция" else "Операция") }
                button(classes = "close") {
                    type = ButtonType.button
                    attributes["aria-label"] = "Закрыть"
                    attributes["onclick"] = "closePanel()"
                    +"×"
                }
            }

            div("seg") {
                listOf(TransactionType.INCOME to "Доход", TransactionType.EXPENSE to "Расход", TransactionType.TRANSFER to "Перевод").forEach { (type, text) ->
                    button(classes = if (type == form.type) "on ${type.name.lowercase()}" else null) {
                        this.type = ButtonType.button
                        redraw("type:${type.name}")
                        +text
                    }
                }
            }

            field("Сумма", if (DraftError.AMOUNT in form.errors) "Введите сумму" else null) {
                div("money") {
                    input(type = InputType.text, name = "amount", classes = "sum") {
                        value = form.amount
                        attributes["inputmode"] = "decimal"
                        attributes["autocomplete"] = "off"
                        // Only when the panel opens: a redraw after a type change or a split must not move the cursor.
                        if (focus) attributes["autofocus"] = "autofocus"
                        attributes["aria-label"] = "Сумма"
                        // A split shows the remainder of the first part; it follows the total.
                        if (form.parts.isNotEmpty()) recount()
                    }
                    span("unit") { +form.currency }
                }
            }

            div("pair") {
                field("Дата") { input(type = InputType.date, name = "date") { value = form.date; attributes["aria-label"] = "Дата" } }
                field("Время") { input(type = InputType.time, name = "time") { value = form.time; attributes["aria-label"] = "Время" } }
            }

            val transfer = form.type == TransactionType.TRANSFER
            field(if (transfer) "Со счёта" else "Счёт", if (DraftError.ACCOUNT in form.errors) "Выберите счёт" else null) {
                choice("account", form.account, listOf(WebOption("", "—")) + form.accounts) {
                    // Another account may bring another currency.
                    attributes["hx-post"] = "/ops/form"
                    attributes["hx-trigger"] = "change"
                }
            }
            if (transfer) {
                field("На счёт", if (DraftError.TO_ACCOUNT in form.errors) "Выберите другой счёт" else null) {
                    choice("toAccount", form.toAccount, listOf(WebOption("", "—")) + form.accounts) {
                        attributes["hx-post"] = "/ops/form"
                        attributes["hx-trigger"] = "change"
                    }
                }
                form.toAmount?.let { toAmount ->
                    field("Пришло на счёт", if (DraftError.TO_AMOUNT in form.errors) "Сколько пришло на счёт" else null) {
                        div("money") {
                            input(type = InputType.text, name = "toAmount", classes = "sum") {
                                value = toAmount
                                attributes["inputmode"] = "decimal"
                                attributes["aria-label"] = "Пришло на счёт"
                            }
                            span("unit") { +form.toCurrency }
                        }
                    }
                }
            } else if (form.parts.isEmpty()) {
                field("Категория") {
                    choice("category", form.category, form.categories)
                    button(classes = "link") {
                        type = ButtonType.button
                        redraw("split")
                        +"Разделить по категориям"
                    }
                }
            } else {
                field("По категориям", if (DraftError.SPLIT in form.errors) "Суммы частей не сходятся с суммой операции" else null) {
                    form.parts.forEachIndexed { i, part ->
                        div("part") {
                            choice("partCategory", part.category, form.categories)
                            if (i == 0) {
                                // The first part takes what the others leave.
                                span("remainder") { id = "remainder"; +form.remainder }
                                span {}
                            } else {
                                input(type = InputType.text, name = "partAmount", classes = "part-amount") {
                                    value = part.amount
                                    attributes["inputmode"] = "decimal"
                                    attributes["aria-label"] = "Сумма части"
                                    recount()
                                }
                                button(classes = "remove") {
                                    type = ButtonType.button
                                    attributes["aria-label"] = "Убрать часть"
                                    redraw("part-remove:$i")
                                    +"×"
                                }
                            }
                        }
                    }
                    button(classes = "link") {
                        type = ButtonType.button
                        redraw("part-add")
                        +"+ Категория"
                    }
                }
            }

            field("Заметка") {
                input(type = InputType.text, name = "note") { value = form.note; attributes["aria-label"] = "Заметка" }
            }
            form.receipt?.let { p("hint") { +it } }

            div("actions") {
                if (form.uid != null) {
                    button(classes = "danger") {
                        type = ButtonType.button
                        attributes["hx-post"] = "/ops/delete"
                        attributes["hx-confirm"] = "Удалить операцию?"
                        +"Удалить"
                    }
                }
                span("spacer") {}
                button(classes = "primary") { type = ButtonType.submit; +"Сохранить" }
            }
            p("hint") { +"Сохраняется на телефоне и уходит на второй телефон через синхронизацию." }
        }
    }

    private fun FlowContent.field(title: String, error: String? = null, content: FlowContent.() -> Unit) =
        div("field") {
            span("label") { +title }
            content()
            if (error != null) span("error") { +error }
        }

    private fun FlowContent.hidden(name: String, value: String) =
        input(type = InputType.hidden, name = name) { this.value = value }

    private fun FlowContent.choice(name: String, selected: String, options: List<WebOption>, extra: kotlinx.html.SELECT.() -> Unit = {}) =
        select {
            this.name = name
            attributes["aria-label"] = LABELS[name] ?: name
            // Attributes go before the options: kotlinx.html writes a tag out once its first child starts.
            extra()
            options.forEach { o -> option { value = o.value; this.selected = o.value == selected; +o.label } }
        }

    private val LABELS = mapOf("account" to "Счёт", "toAccount" to "На счёт", "category" to "Категория", "partCategory" to "Категория части")

    /** Sends the form for a redraw with [action]; the button's own name and value say which. */
    private fun kotlinx.html.BUTTON.redraw(action: String) {
        name = "action"
        value = action
        attributes["hx-post"] = "/ops/form"
    }

    /** Swaps in just the remainder when an amount changes, so typing is not interrupted. */
    private fun kotlinx.html.INPUT.recount() {
        attributes["hx-post"] = "/ops/form"
        attributes["hx-trigger"] = "change"
        attributes["hx-target"] = "#remainder"
        attributes["hx-select"] = "#remainder"
        attributes["hx-swap"] = "outerHTML"
    }
}
