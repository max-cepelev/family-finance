package com.familyfinance.web

import com.familyfinance.domain.model.Category
import com.familyfinance.domain.model.CategoryColors
import com.familyfinance.domain.model.CategoryStats
import com.familyfinance.domain.model.CategoryType
import com.familyfinance.domain.model.MainCurrency
import com.familyfinance.domain.model.Transaction
import com.familyfinance.domain.model.TransactionType
import com.familyfinance.ui.common.MoneyFormatter
import java.net.URLEncoder
import java.text.NumberFormat
import java.time.YearMonth
import java.util.Locale

/** All links encode identifiers as query values, including imported ones. */
internal object WebLinks {
    fun to(path: String, vararg fields: Pair<String, String?>): String = path + fields.filter { it.second != null }
        .joinToString("&", prefix = "?") { (name, value) -> "$name=${URLEncoder.encode(value.orEmpty(), "UTF-8")}" }
}

data class WebStatRow(
    val uid: String?, val name: String, val icon: String, val color: String,
    val amount: String, val share: String, val width: String, val canOpen: Boolean,
)

data class WebStatistics(
    val month: YearMonth, val type: TransactionType, val parent: Category?, val total: String, val rows: List<WebStatRow>,
) {
    companion object {
        fun build(month: YearMonth, type: TransactionType, parentUid: String?, items: List<Transaction>, categories: List<Category>, main: MainCurrency): WebStatistics {
            val kind = if (type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
            val parent = categories.firstOrNull { it.uid == parentUid && !it.deleted && it.type == kind && it.parentUid == null }
            val shares = CategoryStats.shares(items, categories, type, main, parent?.uid)
            val total = shares.sumOf { it.amount }
            val byUid = categories.associateBy { it.uid }
            val colors = CategoryColors.resolve(categories)
            val percent = NumberFormat.getPercentInstance(Locale.forLanguageTag("ru-RU")).apply { maximumFractionDigits = 1 }
            val rows = shares.map { share ->
                val c = byUid[share.categoryUid]
                val fraction = if (total > 0) (share.amount.toDouble() / total).coerceIn(0.0, 1.0) else 0.0
                WebStatRow(
                    uid = c?.uid,
                    name = if (c?.uid == parent?.uid && c != null) "${c.name} (без подкатегории)" else c?.name ?: "Без категории",
                    icon = c?.icon ?: "·",
                    color = colors[c?.uid]?.takeIf { it.matches(Regex("#[0-9A-Fa-f]{6}")) } ?: "#848A99",
                    amount = MoneyFormatter.format(share.amount, main.code),
                    share = percent.format(fraction), width = (fraction * 100).toString(),
                    canOpen = parent == null && c != null && categories.any { !it.deleted && it.parentUid == c.uid },
                )
            }
            return WebStatistics(month, type, parent, MoneyFormatter.format(total, main.code), rows)
        }
    }
}
