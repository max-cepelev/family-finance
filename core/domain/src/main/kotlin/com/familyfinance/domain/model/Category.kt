package com.familyfinance.domain.model

enum class CategoryType { INCOME, EXPENSE }

data class Category(
    override val uid: String,
    /** Null for a top-level category. */
    val parentUid: String?,
    val name: String,
    val type: CategoryType,
    val sortOrder: Int,
    /** Hidden from pickers but still shown on old transactions. */
    val archived: Boolean = false,
    override val updatedAt: Long,
    override val deleted: Boolean = false,
    override val deviceId: String,
    /** Emoji shown instead of the first letter; null for none. */
    val icon: String? = null,
    /**
     * "#22A447", picked by the user; null until then, and then [CategoryColors] gives one. Only top-level
     * categories have their own; subcategories show their parent's.
     */
    val color: String? = null,
) : Syncable
