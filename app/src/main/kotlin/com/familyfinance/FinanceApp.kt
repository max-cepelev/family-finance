package com.familyfinance

import android.app.Application
import com.familyfinance.budget.BudgetWarnings
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FinanceApp : Application() {
    @Inject lateinit var budgetWarnings: BudgetWarnings

    override fun onCreate() {
        super.onCreate()
        budgetWarnings.start()
    }
}
