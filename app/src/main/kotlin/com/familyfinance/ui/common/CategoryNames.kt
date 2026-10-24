package com.familyfinance.ui.common

import com.familyfinance.domain.model.Category

/** Labels and top-level lookup for category uids. */
class CategoryNames(categories: List<Category>) {
    private val byUid = categories.associateBy { it.uid }

    fun get(uid: String?): Category? = uid?.let(byUid::get)

    /** "Питание › обед" for a subcategory, the name for a top-level one. */
    fun label(uid: String?): String? {
        val category = get(uid) ?: return null
        val parent = get(category.parentUid)
        return if (parent == null) category.name else "${parent.name} › ${category.name}"
    }

    /** The top-level category of [uid] (itself when it has no parent). */
    fun top(uid: String?): Category? {
        val category = get(uid) ?: return null
        return get(category.parentUid) ?: category
    }
}
