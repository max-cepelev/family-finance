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
) : Syncable
