package com.familyfinance.ui.transactions

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyfinance.domain.model.Totals
import com.familyfinance.domain.model.TransactionType
import com.familyfinance.ui.common.Amount
import com.familyfinance.ui.common.CategoryAvatar
import com.familyfinance.ui.common.ColoredAmount
import com.familyfinance.ui.common.Dates
import com.familyfinance.ui.common.MoneyFormatter
import com.familyfinance.ui.common.MonthHeader
import com.familyfinance.ui.common.TypedAmount
import com.familyfinance.ui.common.WhiteCard
import com.familyfinance.ui.receipt.DebugReceipts
import com.familyfinance.ui.receipt.ReceiptEvent
import com.familyfinance.ui.receipt.ReceiptMatchSheet
import com.familyfinance.ui.receipt.ReceiptScanViewModel
import com.familyfinance.ui.theme.FinanceColors
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

@Composable
fun TransactionsScreen(
    onAdd: () -> Unit,
    onOpen: (uid: String) -> Unit,
    onOpenReceipt: (receipt: String) -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel(),
    receipts: ReceiptScanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val match by receipts.match.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(receipts) {
        receipts.events.collect { event ->
            // A message must not hold up the next scan, so it is shown on the side and replaced by a newer one.
            snackbar.currentSnackbarData?.dismiss()
            when (event) {
                is ReceiptEvent.OpenForm -> onOpenReceipt(event.receipt)
                is ReceiptEvent.Notice -> scope.launch {
                    val result = snackbar.showSnackbar(
                        event.text,
                        actionLabel = event.openUid?.let { "Открыть" },
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) event.openUid?.let(onOpen)
                }
            }
        }
    }
    // Debug builds take a receipt from `adb shell am start … --es receipt_qr "<QR text>"`, for testing without a camera.
    LaunchedEffect(receipts) {
        DebugReceipts.pending.filterNotNull().collect {
            DebugReceipts.pending.value = null
            receipts.onScanned(it)
        }
    }
    val focusDay by viewModel.focusDay.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Opened from the calendar: scroll to that day once the month is loaded.
    LaunchedEffect(focusDay, state.days) {
        val day = focusDay ?: return@LaunchedEffect
        if (state.loading || state.month.monthValue != day.monthValue || state.month.year != day.year) return@LaunchedEffect
        val index = state.days.indexOfFirst { it.date == day }
        if (index >= 0) listState.scrollToItem(index + 1)
        viewModel.focusConsumed()
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            MonthHeader(state.month, viewModel::showMonth)
            if (!state.loading && state.days.isEmpty()) {
                MonthSummary(state.totals, state.mainCurrency, Modifier.padding(horizontal = 16.dp))
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Нет операций за этот месяц", style = MaterialTheme.typography.bodyLarge, color = FinanceColors.Muted)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.toReview.isNotEmpty()) {
                        item(key = "review") { ReviewBanner(state.toReview.size) { onOpen(state.toReview.first()) } }
                    }
                    item(key = "summary") { MonthSummary(state.totals, state.mainCurrency) }
                    items(state.days, key = { "d:" + it.date }) { day -> DayCard(day, state.mainCurrency, onOpen) }
                }
            }
        }
        Column(
            Modifier.align(Alignment.BottomEnd).padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                onClick = { scanReceipt(context, receipts) },
                shape = RoundedCornerShape(16.dp),
                color = FinanceColors.PrimarySoft,
                contentColor = FinanceColors.OnPrimarySoft,
                shadowElevation = 4.dp,
            ) {
                Row(
                    Modifier.heightIn(min = 44.dp).padding(start = 12.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("Чек", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                }
            }
            FloatingActionButton(
                onClick = onAdd,
                shape = RoundedCornerShape(20.dp),
                containerColor = FinanceColors.Primary,
                contentColor = FinanceColors.Card,
                elevation = FloatingActionButtonDefaults.elevation(6.dp),
            ) { Icon(Icons.Filled.Add, contentDescription = "Добавить операцию") }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp))
    }

    match?.let { m ->
        ReceiptMatchSheet(
            match = m,
            onRemember = receipts::setRemember,
            onAttach = receipts::attach,
            onOther = receipts::otherPurchase,
            onDismiss = receipts::dismiss,
        )
    }
}

/** Google's scanner screen; the code module may still be downloading on the first try. */
private fun scanReceipt(context: Context, receipts: ReceiptScanViewModel) {
    val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    GmsBarcodeScanning.getClient(context, options).startScan()
        .addOnSuccessListener { code -> code.rawValue?.let(receipts::onScanned) }
        // Closing the scanner with "Back" arrives as a failure too (INTERNAL in 16.1.0), so only a scanner that
        // is not there yet (its module still downloading) is worth telling the user about.
        .addOnFailureListener { e ->
            Log.w("ReceiptScan", "scanner closed without a code", e)
            if ((e as? MlKitException)?.errorCode == MlKitException.UNAVAILABLE) receipts.scannerFailed()
        }
}

@Composable
private fun MonthSummary(totals: Totals, currency: String, modifier: Modifier = Modifier) {
    WhiteCard(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCell("Доходы", Modifier.weight(1f), Alignment.Start) {
                ColoredAmount(MoneyFormatter.format(totals.income, currency), FinanceColors.Income, weight = FontWeight.Medium)
            }
            SummaryCell("Расходы", Modifier.weight(1f), Alignment.Start) {
                ColoredAmount(MoneyFormatter.format(totals.expense, currency), FinanceColors.Expense, weight = FontWeight.Medium)
            }
            SummaryCell("Итого", Modifier.weight(1f), Alignment.End) {
                Amount(totals.net, currency, weight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, explicitPlus = true)
            }
        }
    }
}

@Composable
private fun SummaryCell(label: String, modifier: Modifier, align: Alignment.Horizontal, value: @Composable () -> Unit) {
    Column(modifier, horizontalAlignment = align, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = FinanceColors.Muted)
        value()
    }
}

@Composable
private fun DayCard(day: DayUi, currency: String, onOpen: (String) -> Unit) {
    WhiteCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("${day.date.dayOfMonth}", fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Text(Dates.weekday(day.date), style = MaterialTheme.typography.bodySmall, color = FinanceColors.Muted, modifier = Modifier.weight(1f))
            if (day.totals.income != 0L) ColoredAmount(MoneyFormatter.format(day.totals.income, currency), FinanceColors.Income)
            if (day.totals.expense != 0L) ColoredAmount(MoneyFormatter.format(day.totals.expense, currency), FinanceColors.Expense)
        }
        HorizontalDivider(color = FinanceColors.Hairline)
        day.rows.forEach { row -> TransactionRow(row, onClick = { onOpen(row.uid) }) }
    }
}

@Composable
internal fun TransactionRow(row: TransactionRowUi, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // A small bell on the avatar marks operations the app added from a bank notification.
        Box {
            if (row.type == TransactionType.TRANSFER) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(FinanceColors.Track), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = FinanceColors.Muted, modifier = Modifier.size(20.dp))
                }
            } else if (row.needsCategory) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(FinanceColors.Track), contentAlignment = Alignment.Center) {
                    Text("?", color = FinanceColors.Muted, fontWeight = FontWeight.Medium)
                }
            } else {
                CategoryAvatar(row.avatarName, row.avatarKey, icon = row.avatarIcon)
            }
            // The receipt mark wins: it says more about the operation than where it first came from.
            val mark = when {
                row.hasReceipt -> Icons.Outlined.ReceiptLong to "С чеком"
                row.fromPush -> Icons.Outlined.NotificationsNone to "Из уведомления"
                else -> null
            }
            if (mark != null) {
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(x = 3.dp, y = 3.dp).size(18.dp).clip(CircleShape).background(FinanceColors.Card),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(mark.first, contentDescription = mark.second, tint = FinanceColors.Primary, modifier = Modifier.size(13.dp))
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                row.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (row.needsCategory) FinanceColors.Weekend else FinanceColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TypedAmount(row.amount, row.currencyCode, row.type)
    }
}

@Composable
private fun ReviewBanner(count: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFFBF1DC))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.NotificationsNone, contentDescription = null, tint = Color(0xFF6A4A0C))
        Text(
            Dates.plural(count, "операция", "операции", "операций") + " из уведомлений без категории",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF6A4A0C),
            modifier = Modifier.weight(1f),
        )
        Text("Разобрать", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = Color(0xFF6A4A0C))
    }
}
