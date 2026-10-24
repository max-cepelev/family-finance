package com.familyfinance.domain.importer

import com.familyfinance.domain.model.Account
import com.familyfinance.domain.model.AccountGroup
import com.familyfinance.domain.model.Category
import com.familyfinance.domain.model.Currency
import com.familyfinance.domain.model.Transaction

/** Everything read from an external source, already in our model. */
data class ImportBundle(
    val currencies: List<Currency>,
    val groups: List<AccountGroup>,
    val accounts: List<Account>,
    val categories: List<Category>,
    val transactions: List<Transaction>,
    val report: ImportReport,
) {
    fun counts() = ImportCounts(currencies.size, groups.size, accounts.size, categories.size, transactions.size)
}

data class ImportReport(
    val sourceVersion: Int,
    /** Source rows marked deleted that were not carried over. */
    val skippedDeleted: Int,
    /** Pairs of source rows collapsed into one transfer. */
    val transfersMerged: Int,
    /** Rows whose source category is a balance correction; imported without a category. */
    val balanceAdjustments: Int,
    val warningCount: Int,
    /** First warnings, for display; [warningCount] has the total. */
    val warnings: List<String>,
)

data class ImportCounts(
    val currencies: Int,
    val groups: Int,
    val accounts: Int,
    val categories: Int,
    val transactions: Int,
)

data class ImportResult(
    val report: ImportReport,
    val received: ImportCounts,
    /** Records actually written: new, or newer than the local version. */
    val applied: ImportCounts,
)

class ImportFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)
