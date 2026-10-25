package com.familyfinance.ui.banks

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyfinance.domain.bank.Bank
import com.familyfinance.ui.common.TabularNumbers
import com.familyfinance.ui.common.WhiteCard
import com.familyfinance.ui.theme.FinanceColors

/** "Карта Сбера", "по карте Т-Банка". */
fun Bank.genitive(): String = when (this) {
    Bank.SBER -> "Сбера"
    Bank.TBANK -> "Т-Банка"
    Bank.RAIF -> "Райффайзена"
}

private val HintBackground = Color(0xFFFBF1DC)
private val HintInk = Color(0xFF6A4A0C)

@Composable
fun BanksScreen(onClose: () -> Unit, viewModel: BanksViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Back from system settings: the access may have changed.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshAccess() }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
            Text("Уведомления банков", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            WhiteCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().clickable { viewModel.setEnabled(!state.enabled) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Добавлять операции из пушей", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (state.hasAccess) "Доступ к уведомлениям разрешён" else "Нет доступа к уведомлениям",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.hasAccess) FinanceColors.Muted else FinanceColors.Expense,
                        )
                    }
                    Switch(
                        checked = state.enabled,
                        onCheckedChange = viewModel::setEnabled,
                        colors = SwitchDefaults.colors(checkedTrackColor = FinanceColors.Primary),
                    )
                }
                if (!state.hasAccess) {
                    HorizontalDivider(color = FinanceColors.Hairline)
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Android спросит разрешение на доступ к уведомлениям. Если переключатель там серый: " +
                                "Настройки → Приложения → Семейный бюджет → ⋮ → «Разрешить ограниченные настройки».",
                            style = MaterialTheme.typography.bodySmall,
                            color = FinanceColors.Muted,
                        )
                        Button(
                            onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                            colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary),
                        ) { Text("Разрешить доступ", fontWeight = FontWeight.Medium) }
                    }
                }
            }

            state.unknown.forEach { u ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(HintBackground).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Пришло уведомление ${u.bank.genitive()} по новой карте •• ${u.last4} — " +
                            (u.recordedTo?.let { "операция записана на «$it»" } ?: "операция не записана: выберите счёт"),
                        style = MaterialTheme.typography.bodySmall,
                        color = HintInk,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { viewModel.openNew(u.bank, u.last4, accountUid = u.recordedAccountUid) }) { Text("Привязать", color = HintInk, fontWeight = FontWeight.Medium) }
                }
            }

            state.sections.forEach { section ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(section.bank.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = FinanceColors.Muted, modifier = Modifier.padding(horizontal = 4.dp))
                    WhiteCard(Modifier.fillMaxWidth()) {
                        section.cards.forEach { row ->
                            CardLine("•• ${row.card.last4}", row.accountName, FinanceColors.Ink) { viewModel.openExisting(row.card) }
                            HorizontalDivider(color = FinanceColors.Hairline)
                        }
                        val fallback = section.fallback
                        CardLine("Без номера", fallback?.accountName ?: "не выбран", FinanceColors.Muted) {
                            if (fallback != null) viewModel.openExisting(fallback.card) else viewModel.openNew(section.bank, fallback = true)
                        }
                        HorizontalDivider(color = FinanceColors.Hairline)
                        Row(
                            Modifier.fillMaxWidth().clickable { viewModel.openNew(section.bank) }.heightIn(min = 44.dp).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = FinanceColors.Primary, modifier = Modifier.size(18.dp))
                            Text("Добавить карту", color = FinanceColors.Primary, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    state.edit?.let { edit ->
        CardSheet(
            edit = edit,
            accounts = state.accounts,
            onDigits = viewModel::setDigits,
            onAccount = viewModel::pickAccount,
            onDelete = viewModel::delete,
            onSave = viewModel::save,
            onDismiss = viewModel::dismiss,
        )
    }
}

@Composable
private fun CardLine(card: String, account: String, cardColor: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 50.dp).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(card, style = MaterialTheme.typography.bodyLarge.merge(TabularNumbers), fontWeight = FontWeight.Medium, color = cardColor, modifier = Modifier.width(92.dp))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = FinanceColors.ChipBorder, modifier = Modifier.size(16.dp))
        Text(account, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}
