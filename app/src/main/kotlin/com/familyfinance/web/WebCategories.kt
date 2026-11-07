package com.familyfinance.web

import com.familyfinance.domain.model.Category
import com.familyfinance.domain.model.CategoryType
import com.familyfinance.ui.common.CategoryNames

/** Catalog rows keep archived names visible for historical operations. */
data class WebCategoryRow(val uid: String, val name: String, val icon: String, val operations: Int)
data class WebCategoryGroup(val top: WebCategoryRow, val children: List<WebCategoryRow>)
data class WebCategories(val type: CategoryType, val groups: List<WebCategoryGroup>, val archived: List<WebCategoryRow>) {
    companion object {
        fun build(type: CategoryType, categories: List<Category>, usage: Map<String, Int>): WebCategories {
            val all = categories.filter { !it.deleted && it.type == type }
            val visible = all.filterNot { it.archived }
            val visibleIds = visible.map { it.uid }.toSet()
            val names = CategoryNames(all)
            // Every catalog link opens the category and its children, archived or visible.
            fun row(c: Category) = WebCategoryRow(c.uid, c.name, c.icon ?: names.top(c.uid)?.icon ?: "·",
                (usage[c.uid] ?: 0) + all.filter { it.parentUid == c.uid }.sumOf { usage[it.uid] ?: 0 })
            val groups = visible.filter { it.parentUid !in visibleIds }.sortedBy { it.sortOrder }.map { top ->
                val children = visible.filter { it.parentUid == top.uid }.sortedBy { it.sortOrder }
                WebCategoryGroup(row(top), children.map { row(it) })
            }
            return WebCategories(type, groups, all.filter { it.archived }.sortedBy { it.sortOrder }.map { row(it) })
        }
    }
}
