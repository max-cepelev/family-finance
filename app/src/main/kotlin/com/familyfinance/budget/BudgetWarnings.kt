package com.familyfinance.budget

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.familyfinance.MainActivity
import com.familyfinance.R
import com.familyfinance.domain.model.BudgetState
import com.familyfinance.domain.model.Budgets
import com.familyfinance.domain.model.MainCurrency
import com.familyfinance.domain.repository.AccountRepository
import com.familyfinance.domain.repository.BudgetRepository
import com.familyfinance.domain.repository.CategoryRepository
import com.familyfinance.domain.repository.TransactionRepository
import com.familyfinance.ui.common.MoneyFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * "Предупредить на 80% и 100%": while the app's process runs (the bank notification listener keeps it running),
 * watches this month's spending against the budgets and notifies once per threshold per budget per month.
 */
@Singleton
class BudgetWarnings @Inject constructor(
    @ApplicationContext private val context: Context,
    private val budgets: BudgetRepository,
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val accounts: AccountRepository,
) {
    private val prefs = context.getSharedPreferences("budget_warnings", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        scope.launch {
            currentMonth().flatMapLatest { month ->
                val zone = ZoneId.systemDefault()
                val from = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
                combine(budgets.observeAll(), transactions.observeBetween(from, to), categories.observeAll(), accounts.observeCurrencies()) { list, items, cats, currencies ->
                    val main = MainCurrency.from(currencies)
                    val names = cats.associate { it.uid to it.name }
                    Budgets.progress(list.filter { it.warn }, items, cats, main).forEach { state ->
                        val reached = Budgets.reached(state)
                        if (reached > shown(state, month)) {
                            remember(state, month, reached)
                            notify(state, reached, state.budget.categoryUid?.let(names::get) ?: "Все расходы", main.code)
                        }
                    }
                }
            }.collect {}
        }
    }

    /** A budget just saved: what it has already reached this month is not news. */
    fun markReached(state: BudgetState) = remember(state, YearMonth.now(), Budgets.reached(state))

    private fun key(state: BudgetState, month: YearMonth) = "${state.budget.uid}:$month"
    private fun shown(state: BudgetState, month: YearMonth) = prefs.getInt(key(state, month), 0)
    private fun remember(state: BudgetState, month: YearMonth, reached: Int) = prefs.edit().putInt(key(state, month), reached).apply()

    private fun notify(state: BudgetState, reached: Int, name: String, currency: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
        ) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Бюджеты", NotificationManager.IMPORTANCE_DEFAULT))
        val text = if (state.spent > state.budget.amount) {
            "Бюджет превышен на ${MoneyFormatter.format(-state.left, currency)}"
        } else {
            "Потрачено $reached% — осталось ${MoneyFormatter.format(state.left, currency)}"
        }
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(name)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(state.budget.uid.hashCode(), notification)
    }

    /** This month, again after each midnight so a new month starts with new thresholds. */
    private fun currentMonth() = flow {
        while (true) {
            emit(YearMonth.now())
            val now = java.time.LocalDateTime.now()
            delay(Duration.between(now, LocalDate.now().plusDays(1).atStartOfDay()).toMillis() + 1_000)
        }
    }.distinctUntilChanged()

    private companion object {
        const val CHANNEL = "budgets"
    }
}
