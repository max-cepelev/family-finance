package com.familyfinance.domain.model

/**
 * Colours of categories: the tile behind the emoji and the slice in statistics. A top-level category shows the
 * colour the user picked, or one from [PALETTE] by its place among its type's categories, so both phones agree
 * without writing anything; a subcategory shows its parent's.
 */
object CategoryColors {
    /** Three rows of six in the picker: greens and blues, purples and reds, warm and neutral. */
    val PALETTE = listOf(
        "#22A447", "#1FAE84", "#11999E", "#0B95B8", "#1A73E8", "#2F4A9E",
        "#5257D9", "#9450DB", "#B92FB0", "#DB3B76", "#E8607F", "#E0453A",
        "#E7720D", "#C98A0A", "#6E8B1E", "#9C6B3C", "#4D5566", "#848A99",
    )

    /** Colour of every category by uid. */
    fun resolve(categories: List<Category>): Map<String, String> {
        val tops = categories.filter { it.parentUid == null && !it.deleted }
        val own = HashMap<String, String>()
        for (type in CategoryType.entries) {
            val ofType = tops.filter { it.type == type }.sortedBy { it.sortOrder }
            // Automatic colours skip the picked ones, so a picked colour stays one category's.
            val picked = ofType.mapNotNull { it.color }.toSet()
            val free = PALETTE.filter { it !in picked }.ifEmpty { PALETTE }
            var next = 0
            ofType.forEach { c -> own[c.uid] = c.color ?: free[next++ % free.size] }
        }
        return categories.associate { c -> c.uid to (own[c.parentUid ?: c.uid] ?: c.color ?: PALETTE.last()) }
    }

    /** For a new top-level category: the first colour no category of its type shows yet. */
    fun nextFree(categories: List<Category>, type: CategoryType): String {
        val used = resolve(categories.filter { it.type == type }).values.toSet()
        return PALETTE.firstOrNull { it !in used } ?: PALETTE[categories.count { it.type == type && it.parentUid == null } % PALETTE.size]
    }
}
