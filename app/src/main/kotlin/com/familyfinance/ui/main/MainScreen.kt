package com.familyfinance.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.familyfinance.ui.account.AccountEditScreen
import com.familyfinance.ui.account.AccountEditViewModel
import com.familyfinance.ui.accounts.AccountsScreen
import com.familyfinance.ui.categories.CategoriesScreen
import com.familyfinance.ui.common.SheetHost
import com.familyfinance.ui.calendar.CalendarScreen
import com.familyfinance.ui.more.MoreScreen
import com.familyfinance.ui.stats.StatsScreen
import com.familyfinance.ui.sync.SyncScreen
import com.familyfinance.ui.theme.FinanceColors
import com.familyfinance.ui.transaction.EditTransactionScreen
import com.familyfinance.ui.transaction.EditTransactionViewModel
import com.familyfinance.ui.transactions.TransactionsScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Transactions("transactions", "Операции", Icons.AutoMirrored.Outlined.ReceiptLong),
    Calendar("calendar", "Календарь", Icons.Outlined.CalendarMonth),
    Stats("stats", "Статистика", Icons.Outlined.BarChart),
    Accounts("accounts", "Счета", Icons.Outlined.AccountBalanceWallet),
    More("more", "Ещё", Icons.Outlined.MoreHoriz),
}

private const val EDIT_ROUTE = "transaction?${EditTransactionViewModel.ARG_UID}={${EditTransactionViewModel.ARG_UID}}"

private const val SYNC_ROUTE = "sync"
private const val CATEGORIES_ROUTE = "categories"
private const val ACCOUNT_ROUTE = "account?${AccountEditViewModel.ARG_UID}={${AccountEditViewModel.ARG_UID}}"

private fun accountRoute(uid: String?) =
    if (uid == null) "account" else "account?${AccountEditViewModel.ARG_UID}=$uid"

private fun editRoute(uid: String?) =
    if (uid == null) "transaction" else "transaction?${EditTransactionViewModel.ARG_UID}=$uid"

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

    SheetHost {
        Scaffold(
            // Each screen handles the status bar inset itself.
            contentWindowInsets = WindowInsets(0),
            containerColor = FinanceColors.Ground,
            bottomBar = {
                if (onTab) {
                    Column {
                        HorizontalDivider(color = FinanceColors.Hairline)
                        NavigationBar(containerColor = FinanceColors.Card, tonalElevation = 0.dp) {
                            Tab.entries.forEach { tab ->
                                val selected = destination?.hierarchy?.any { it.route == tab.route } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { openTab(tab) },
                                    icon = { Icon(tab.icon, contentDescription = null) },
                                    // Five tabs on a phone leave ~80dp each: keep labels on one line.
                                    label = {
                                        Text(
                                            tab.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (selected) FontWeight.Medium else null,
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = FinanceColors.OnPrimarySoft,
                                        selectedTextColor = FinanceColors.Ink,
                                        indicatorColor = FinanceColors.PrimarySoft,
                                        unselectedIconColor = FinanceColors.Muted,
                                        unselectedTextColor = FinanceColors.Muted,
                                    ),
                                )
                            }
                        }
                    }
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
                    )
                }
                composable(Tab.Calendar.route) { CalendarScreen(onOpenDay = { openTab(Tab.Transactions) }) }
                composable(Tab.Stats.route) { StatsScreen() }
                composable(Tab.Accounts.route) {
                    AccountsScreen(onOpenAccount = { uid -> navController.navigate(accountRoute(uid)) })
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
                    )
                }
                composable(CATEGORIES_ROUTE) { CategoriesScreen(onClose = { navController.popBackStack() }) }
                composable(SYNC_ROUTE) { SyncScreen(onClose = { navController.popBackStack() }) }
                composable(
                    EDIT_ROUTE,
                    arguments = listOf(
                        navArgument(EditTransactionViewModel.ARG_UID) {
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
    }
}
