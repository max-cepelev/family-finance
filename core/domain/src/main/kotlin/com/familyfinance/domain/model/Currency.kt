package com.familyfinance.domain.model

/** [uid] is the ISO code ("RUB"), so two devices adding the same currency converge on one record. */
data class Currency(
    override val uid: String,
    val name: String,
    val symbol: String,
    val decimals: Int,
    val isMain: Boolean,
    /** How many units of the main currency one unit of this currency is worth. */
    val rateToMain: Double,
    override val updatedAt: Long,
    override val deleted: Boolean = false,
    override val deviceId: String,
) : Syncable {
    val code: String get() = uid
}
