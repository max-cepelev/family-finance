package com.familyfinance.data.push

import com.familyfinance.data.db.FinanceDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** A card named in a notification that is not linked to an account yet. */
data class UnknownCard(val bank: String, val last4: String, val recordedAccountUid: String?)

class UnknownCards @Inject constructor(private val db: FinanceDatabase) {
    fun observe(): Flow<List<UnknownCard>> =
        db.bankDao().observeUnknown().map { rows -> rows.map { UnknownCard(it.bank, it.last4, it.recordedAccountUid) } }
}
