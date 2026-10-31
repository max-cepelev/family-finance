package com.familyfinance.ui.main

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyfinance.ui.account.AccountEditScreen
import com.familyfinance.ui.account.AccountEditViewModel
import com.familyfinance.ui.accounts.AccountOperationsScreen
import com.familyfinance.ui.accounts.AccountOperationsViewModel
import com.familyfinance.ui.accounts.AccountsScreen
import com.familyfinance.ui.banks.BanksScreen
import com.familyfinance.ui.budget.BudgetsScreen
import com.familyfinance.ui.calendar.CalendarScreen
import com.familyfinance.ui.categories.CategoriesScreen
import com.familyfinance.ui.common.LocalCategoryColors
import com.familyfinance.ui.common.SheetHost
import com.familyfinance.ui.csv.CsvImportScreen
import com.familyfinance.ui.csv.CsvImportViewModel
import com.familyfinance.ui.more.MoreScreen
import com.familyfinance.ui.stats.StatsScreen
import com.familyfinance.ui.sync.SyncScreen
import com.familyfinance.ui.theme.FinanceColors
import com.familyfinance.ui.transaction.EditTransactionScreen
import com.familyfinance.ui.transaction.EditTransactionViewModel
import com.familyfinance.ui.transactions.TransactionsScreen
import com.familyfinance.ui.web.WebAccessScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Transactions("transactions", "Операции", Icons.AutoMirrored.Outlined.ReceiptLong),
    Calendar("calendar", "Календарь", Icons.Outlined.CalendarMonth),
    Stats("stats", "Статистика", Icons.Outlined.BarChart),
    Accounts("accounts", "Счета", Icons.Outlined.AccountBalanceWallet),
    More("more", "Ещё", Icons.Outlined.MoreHoriz),
}

private const val EDIT_ROUTE = "transaction?${EditTransactionViewModel.ARG_UID}={${EditTransactionViewModel.ARG_UID}}" +
    "&${EditTransactionViewModel.ARG_RECEIPT}={${EditTransactionViewModel.ARG_RECEIPT}}" +
    "&${EditTransactionViewModel.ARG_ACCOUNT}={${EditTransactionViewModel.ARG_ACCOUNT}}"
private const val ACCOUNT_OPS_ROUTE = "accountOps/{${AccountOperationsViewModel.ARG_UID}}"

private const val SYNC_ROUTE = "sync"
private const val CATEGORIES_ROUTE = "categories"
private const val BANKS_ROUTE = "banks"
private const val WEB_ROUTE = "web"
private const val BUDGETS_ROUTE = "budgets"
private const val CSV_ROUTE = "csv?${CsvImportViewModel.ARG_URI}={${CsvImportViewModel.ARG_URI}}"
private const val ACCOUNT_ROUTE = "account?${AccountEditViewModel.ARG_UID}={${AccountEditViewModel.ARG_UID}}"

private fun accountRoute(uid: String?) =
    if (uid == null) "account" else "account?${AccountEditViewModel.ARG_UID}=$uid"

private fun editRoute(uid: String?) =
    if (uid == null) "transaction" else "transaction?${EditTransactionViewModel.ARG_UID}=$uid"

/** A new operation on [accountUid], from that account's screen. */
private fun newOnAccountRoute(accountUid: String) = "transaction?${EditTransactionViewModel.ARG_ACCOUNT}=$accountUid"

/** A new operation filled in from a scanned receipt. */
private fun receiptRoute(receipt: String) = "transaction?${EditTransactionViewModel.ARG_RECEIPT}=${Uri.encode(receipt)}"

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val onTab = Tab.entries.any { tab -> destination?.hierarchy?.any { it.route == tab.route } == true }

    fun openTab(tab: Tab) = navController.navigate(tab.route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    val categoryColors by hiltViewModel<CategoryColorsViewModel>().colors.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalCategoryColors provides categoryColors) { SheetHost {
        Scaffold(
            // Each screen handles the status bar inset itself.
            contentWindowInsets = WindowInsets(0),
            containerColor = FinanceColors.Ground,
            bottomBar = {
                if (onTab) {
                    FloatingTabBar(selected = { tab -> destination?.hierarchy?.any { it.route == tab.route } == true }, onSelect = ::openTab)
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Tab.Transactions.route,
                modifier = Modifier.padding(padding),
            ) {
                composable(Tab.Transactions.route) {
                    TransactionsScreen(
                        onAdd = { navController.navigate(editRoute(null)) },
                        onOpen = { uid -> navController.navigate(editRoute(uid)) },
                        onOpenReceipt = { receipt -> navController.navigate(receiptRoute(receipt)) },
                    )
                }
                composable(Tab.Calendar.route) { CalendarScreen(onOpenDay = { openTab(Tab.Transactions) }) }
                composable(Tab.Stats.route) { StatsScreen() }
                composable(Tab.Accounts.route) {
                    // An account opens its operations; a new one opens straight in the editor.
                    AccountsScreen(onOpenAccount = { uid -> navController.navigate(if (uid == null) accountRoute(null) else "accountOps/$uid") })
                }
                composable(
                    ACCOUNT_OPS_ROUTE,
                    arguments = listOf(navArgument(AccountOperationsViewModel.ARG_UID) { type = NavType.StringType }),
                ) {
                    AccountOperationsScreen(
                        onClose = { navController.popBackStack() },
                        onEditAccount = { navController.navigate(accountRoute(it)) },
                        onOpenOperation = { navController.navigate(editRoute(it)) },
                        onAddOperation = { navController.navigate(newOnAccountRoute(it)) },
                    )
                }
                composable(
                    ACCOUNT_ROUTE,
                    arguments = listOf(
                        navArgument(AccountEditViewModel.ARG_UID) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                ) { AccountEditScreen(onClose = { navController.popBackStack() }) }
                composable(Tab.More.route) {
                    MoreScreen(
                        onOpenSync = { navController.navigate(SYNC_ROUTE) },
                        onOpenCategories = { navController.navigate(CATEGORIES_ROUTE) },
                        onOpenBanks = { navController.navigate(BANKS_ROUTE) },
                        onOpenWeb = { navController.navigate(WEB_ROUTE) },
                        onOpenBudgets = { navController.navigate(BUDGETS_ROUTE) },
                        onOpenCsv = { uri -> navController.navigate("csv?${CsvImportViewModel.ARG_URI}=${Uri.encode(uri.toString())}") },
                    )
                }
                composable(CATEGORIES_ROUTE) { CategoriesScreen(onClose = { navController.popBackStack() }) }
                composable(BANKS_ROUTE) { BanksScreen(onClose = { navController.popBackStack() }) }
                composable(WEB_ROUTE) { WebAccessScreen(onClose = { navController.popBackStack() }) }
                composable(BUDGETS_ROUTE) { BudgetsScreen(onClose = { navController.popBackStack() }) }
                composable(
                    CSV_ROUTE,
                    arguments = listOf(navArgument(CsvImportViewModel.ARG_URI) { type = NavType.StringType; defaultValue = "" }),
                ) { CsvImportScreen(onClose = { navController.popBackStack() }) }
                composable(SYNC_ROUTE) { SyncScreen(onClose = { navController.popBackStack() }) }
                composable(
                    EDIT_ROUTE,
                    arguments = listOf(
                        navArgument(EditTransactionViewModel.ARG_UID) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument(EditTransactionViewModel.ARG_RECEIPT) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument(EditTransactionViewModel.ARG_ACCOUNT) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                ) {
                    EditTransactionScreen(onClose = { navController.popBackStack() })
                }
            }
        }
    } }
}

/** The tabs as a white pill floating over the ground, the open one tinted. */
@Composable
private fun FloatingTabBar(selected: (Tab) -> Boolean, onSelect: (Tab) -> Unit) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = FinanceColors.Card,
        shadowElevation = 10.dp,
        modifier = Modifier.navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 10.dp).fillMaxWidth(),
    ) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Tab.entries.forEach { tab ->
                val on = selected(tab)
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (on) FinanceColors.PrimarySoft else Color.Transparent)
                        .selectable(selected = on, role = Role.Tab) { onSelect(tab) }
                        .padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(tab.icon, contentDescription = null, tint = if (on) FinanceColors.Primary else FinanceColors.Muted, modifier = Modifier.size(22.dp))
                    // Five tabs on a phone leave ~70dp each: keep labels on one line.
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = (-0.1).sp),
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (on) FinanceColors.Primary else FinanceColors.Muted,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}
