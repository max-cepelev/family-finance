package com.familyfinance.domain.model

/** Sum of one category in the main currency; [categoryUid] null means "no category". */
data class CategoryShare(val categoryUid: String?, val amount: Long)

object CategoryStats {

    /**
     * Totals per category for one operation [type], largest first.
     *
     * With [parentUid] null, subcategories roll up into their top-level category. With a [parentUid],
     * only that category's operations count, split by subcategory (operations booked on the parent
     * itself stay under [parentUid]).
     */
    fun shares(
        transactions: Iterable<Transaction>,
        categories: List<Category>,
        type: TransactionType,
        main: MainCurrency,
        parentUid: String? = null,
    ): List<CategoryShare> {
        require(type != TransactionType.TRANSFER) { "Transfers have no categories" }
        val byUid = categories.associateBy { it.uid }
        val sums = LinkedHashMap<String?, Long>()
        for (tx in transactions) {
            if (tx.deleted || tx.type != type) continue
            val category = tx.categoryUid?.let(byUid::get)
            val key = if (parentUid == null) {
                category?.let { it.parentUid?.takeIf(byUid::containsKey) ?: it.uid }
            } else {
                if (category == null || (category.uid != parentUid && category.parentUid != parentUid)) continue
                category.uid
            }
            sums.merge(key, main.convert(tx.amount, tx.currencyCode), Long::plus)
        }
        return sums.map { (uid, amount) -> CategoryShare(uid, amount) }.sortedByDescending { it.amount }
    }
}
