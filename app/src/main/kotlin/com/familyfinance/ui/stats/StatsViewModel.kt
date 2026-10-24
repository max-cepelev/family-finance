package com.familyfinance.ui.stats

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyfinance.domain.model.Category
import com.familyfinance.domain.model.CategoryStats
import com.familyfinance.domain.model.Daily
import com.familyfinance.domain.model.MainCurrency
import com.familyfinance.domain.model.Period
import com.familyfinance.domain.model.Totals
import com.familyfinance.domain.model.TransactionType
import com.familyfinance.domain.repository.AccountRepository
import com.familyfinance.domain.repository.CategoryRepository
import com.familyfinance.domain.repository.TransactionRepository
import com.familyfinance.ui.common.CategoryPalette
import com.familyfinance.ui.common.SelectedPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StatRow(
    val categoryUid: String?,
    val name: String,
    val amount: Long,
    /** Share of the shown total, 0..1. */
    val share: Float,
    /** Relative to the largest row, 0..1, for the bar. */
    val bar: Float,
    val color: Color,
    /** Has subcategories to drill into. */
    val canOpen: Boolean,
)

data class DonutSlice(val fraction: Float, val color: Color)

enum class PeriodKind { WEEK, MONTH, YEAR, CUSTOM }

/** The "Свой период" sheet while it is open: the picked days and what they would show. */
data class CustomPeriodDraft(val period: Period.Custom, val totals: Totals? = null)

data class StatsUiState(
    val period: Period,
    val kind: PeriodKind = PeriodKind.MONTH,
    val loading: Boolean = true,
    val mainCurrency: String = "RUB",
    val type: TransactionType = TransactionType.EXPENSE,
    /** Income and expense of the whole period, for the toggle. */
    val periodTotals: Totals = Totals(),
    /** Category being drilled into, null on the top level. */
    val parent: Category? = null,
    val total: Long = 0,
    val rows: List<StatRow> = emptyList(),
    val slices: List<DonutSlice> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val selected: SelectedPeriod,
) : ViewModel() {

    private val zone = ZoneId.systemDefault()
    private val type = MutableStateFlow(TransactionType.EXPENSE)
    private val parentUid = MutableStateFlow<String?>(null)
    private val kind = MutableStateFlow(PeriodKind.MONTH)
    /** Week, year or custom period; a month always comes from the month shared with the other tabs. */
    private val ownPeriod = MutableStateFlow<Period?>(null)
    private val draft = MutableStateFlow<Period.Custom?>(null)

    private val shown: Flow<Period> = combine(kind, selected.month, ownPeriod) { k, month, own ->
        if (k == PeriodKind.MONTH || own == null) Period.Month(month) else own
    }.distinctUntilChanged()

    /** The open "Свой период" sheet with a preview of the picked days. */
    val customDraft: StateFlow<CustomPeriodDraft?> = draft.flatMapLatest { picked ->
        if (picked == null) return@flatMapLatest flowOf(null)
        val (from, to) = picked.millis(zone)
        combine(transactions.observeBetween(from, to), accounts.observeCurrencies()) { items, currencies ->
            CustomPeriodDraft(picked, Daily.totals(items, MainCurrency.from(currencies)))
        }.onStart { emit(CustomPeriodDraft(picked)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val state: StateFlow<StatsUiState> = shown.flatMapLatest { period ->
        val (from, to) = period.millis(zone)
        combine(
            transactions.observeBetween(from, to),
            categories.observeAll(),
            accounts.observeCurrencies(),
            combine(type, parentUid, kind) { t, p, k -> Triple(t, p, k) },
        ) { items, categoryList, currencies, (shownType, parentId, shownKind) ->
            val main = MainCurrency.from(currencies)
            val byUid = categoryList.associateBy { it.uid }
            val parent = parentId?.let(byUid::get)
            val shares = CategoryStats.shares(items, categoryList, shownType, main, parent?.uid)
            val total = shares.sumOf { it.amount }
            val max = shares.maxOfOrNull { it.amount } ?: 0L
            val withChildren = categoryList.mapNotNullTo(HashSet()) { it.parentUid }
            val rows = shares.mapIndexed { index, share ->
                val category = share.categoryUid?.let(byUid::get)
                StatRow(
                    categoryUid = share.categoryUid,
                    name = when {
                        category == null -> "Без категории"
                        parent != null && category.uid == parent.uid -> "${parent.name} (без подкатегории)"
                        else -> category.name
                    },
                    amount = share.amount,
                    share = if (total > 0) share.amount.toFloat() / total else 0f,
                    bar = if (max > 0) share.amount.toFloat() / max else 0f,
                    color = CategoryPalette.rank(index),
                    canOpen = parent == null && category != null && category.uid in withChildren,
                )
            }
            StatsUiState(
                period = period,
                kind = if (period is Period.Month) PeriodKind.MONTH else shownKind,
                loading = false,
                mainCurrency = main.code,
                type = shownType,
                periodTotals = Daily.totals(items, main),
                parent = parent,
                total = total,
                rows = rows,
                slices = slices(rows),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState(Period.Month(selected.month.value)))

    /** Switches between week, month and year around what is shown now; custom opens the sheet instead. */
    fun showKind(value: PeriodKind) {
        val current = state.value.period
        val today = LocalDate.now(zone)
        // Stay where the user is looking; on the current period that means today.
        val anchor = if (!today.isBefore(current.start) && today.isBefore(current.endExclusive)) today else current.start
        when (value) {
            PeriodKind.MONTH -> selected.show(YearMonth.from(anchor))
            PeriodKind.WEEK -> ownPeriod.value = Period.Week.containing(anchor)
            PeriodKind.YEAR -> ownPeriod.value = Period.Year(anchor.year)
            PeriodKind.CUSTOM -> {
                openCustom()
                return
            }
        }
        kind.value = value
        parentUid.value = null
    }

    fun previous() = step(forward = false)
    fun next() = step(forward = true)

    private fun step(forward: Boolean) {
        val current = state.value.period
        if (current is Period.Month) {
            selected.show(if (forward) current.month.plusMonths(1) else current.month.minusMonths(1))
        } else {
            ownPeriod.value = (if (forward) current.next() else current.previous()) ?: return
        }
    }

    fun showMonth(month: YearMonth) = selected.show(month)

    fun openCustom() {
        val current = state.value.period
        draft.value = current as? Period.Custom ?: Period.Custom(current.start, current.endExclusive.minusDays(1))
    }

    /** Picking "С" after "По" (or the other way round) moves the other end along. */
    fun setCustomFrom(day: LocalDate) = draft.update { it?.let { Period.Custom(day, maxOf(day, it.to)) } }
    fun setCustomTo(day: LocalDate) = draft.update { it?.let { Period.Custom(minOf(day, it.from), day) } }

    fun applyPreset(preset: CustomPreset) {
        val today = LocalDate.now(zone)
        viewModelScope.launch {
            draft.value = when (preset) {
                CustomPreset.LAST_3_MONTHS -> Period.Custom.lastThreeMonths(today)
                CustomPreset.YEAR_TO_DATE -> Period.Custom.yearToDate(today)
                CustomPreset.LAST_YEAR -> Period.Custom.lastYear(today)
                CustomPreset.ALL_TIME -> Period.Custom.allTime(
                    transactions.firstDate()?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() },
                    today,
                )
            }
        }
    }

    fun confirmCustom() {
        val picked = draft.value ?: return
        ownPeriod.value = picked
        kind.value = PeriodKind.CUSTOM
        parentUid.value = null
        draft.value = null
    }

    fun dismissCustom() {
        draft.value = null
    }

    fun showType(value: TransactionType) {
        type.value = value
        parentUid.value = null
    }

    fun open(categoryUid: String) {
        parentUid.value = categoryUid
    }

    fun back() {
        parentUid.value = null
    }

    /** The ranked categories get their own slice, the rest share one grey slice. */
    private fun slices(rows: List<StatRow>): List<DonutSlice> {
        val ranked = rows.take(CategoryPalette.RANKED).map { DonutSlice(it.share, it.color) }
        val rest = rows.drop(CategoryPalette.RANKED).sumOf { it.share.toDouble() }.toFloat()
        return if (rest > 0f) ranked + DonutSlice(rest, CategoryPalette.Rest) else ranked
    }
}

enum class CustomPreset(val label: String) {
    LAST_3_MONTHS("Последние 3 месяца"),
    YEAR_TO_DATE("С начала года"),
    LAST_YEAR("Прошлый год"),
    ALL_TIME("Всё время"),
}
