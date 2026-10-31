package com.familyfinance.domain.model

/**
 * A monthly spending limit, the same every month until removed: for one category with its subcategories, or for
 * all expenses when [categoryUid] is null. [amount] is in minor units of the main currency.
 */
data class Budget(
    override val uid: String,
    val categoryUid: String?,
    val amount: Long,
    /** Notify at 80% and at 100% of [amount]. */
    val warn: Boolean = true,
    override val updatedAt: Long,
    override val deleted: Boolean = false,
    override val deviceId: String,
) : Syncable
