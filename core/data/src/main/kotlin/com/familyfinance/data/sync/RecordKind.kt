package com.familyfinance.data.sync

import com.familyfinance.domain.model.Account
import com.familyfinance.domain.model.AccountGroup
import com.familyfinance.domain.model.BankCard
import com.familyfinance.domain.model.CashRegister
import com.familyfinance.domain.model.Category
import com.familyfinance.domain.model.Currency
import com.familyfinance.domain.model.Syncable
import com.familyfinance.domain.model.Transaction

/** Kind column of the outbox; the values are stored, never rename them. */
object RecordKind {
    const val CURRENCY = "currency"
    const val GROUP = "group"
    const val ACCOUNT = "account"
    const val CATEGORY = "category"
    const val TRANSACTION = "tx"
    const val CARD = "card"
    const val REGISTER = "register"

    fun of(record: Syncable): String = when (record) {
        is Currency -> CURRENCY
        is AccountGroup -> GROUP
        is Account -> ACCOUNT
        is Category -> CATEGORY
        is Transaction -> TRANSACTION
        is BankCard -> CARD
        is CashRegister -> REGISTER
        else -> error("Unknown record type ${record::class.simpleName}")
    }
}
