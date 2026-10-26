package com.familyfinance.domain.model

/**
 * What a cash register's receipts are: the shop name and category the user gave the first receipt from it.
 * [uid] is the register's fiscal storage number, the same on every phone, so both phones share one record.
 */
data class CashRegister(
    override val uid: String,
    val payee: String,
    val categoryUid: String?,
    override val updatedAt: Long,
    override val deleted: Boolean = false,
    override val deviceId: String,
) : Syncable
