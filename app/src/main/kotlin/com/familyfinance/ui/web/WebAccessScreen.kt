package com.familyfinance.ui.web

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyfinance.ui.common.CardShape
import com.familyfinance.ui.common.Dates
import com.familyfinance.ui.common.WhiteCard
import com.familyfinance.ui.theme.FinanceColors
import com.familyfinance.web.WebAccessState

/** "Доступ с компьютера": the address and code to open the budget in a browser on the same Wi-Fi. */
@Composable
fun WebAccessScreen(onClose: () -> Unit, viewModel: WebAccessViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The notification that keeps the server alive needs this on Android 13+; without it the server still works.
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.start() }
    val start = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) else viewModel.start()
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
            Text("Доступ с компьютера", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.running) Running(state, viewModel::disconnect) else Off(state.error)
            Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = FinanceColors.Muted, modifier = Modifier.size(18.dp))
                Text(
                    "Работает только внутри этой Wi-Fi. Код новый при каждом включении. В общественных сетях лучше не включать.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FinanceColors.Muted,
                )
            }
        }
        if (state.running) {
            OutlinedButton(
                onClick = viewModel::stop,
                border = BorderStroke(1.dp, FinanceColors.Expense),
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
            ) { Text("Выключить доступ", color = FinanceColors.Expense, fontWeight = FontWeight.Medium) }
        } else {
            Button(
                onClick = start,
                colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary),
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
            ) { Text("Включить доступ", fontWeight = FontWeight.Medium) }
        }
    }
}

@Composable
private fun Off(error: String?) {
    WhiteCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Бюджет в браузере компьютера", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                "Телефон откроет страницу в домашней Wi-Fi: на компьютере ничего ставить не нужно. Данные остаются на телефоне, " +
                    "правки из браузера сохраняются здесь же и уходят на второй телефон.",
                style = MaterialTheme.typography.bodyMedium,
                color = FinanceColors.Muted,
            )
            if (error != null) Text(error, style = MaterialTheme.typography.bodyMedium, color = FinanceColors.Expense)
        }
    }
}

@Composable
private fun Running(state: WebAccessState, onDisconnect: (String) -> Unit) {
    Surface(shape = CardShape, color = FinanceColors.Primary, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Wifi, contentDescription = null, tint = FinanceColors.OnPrimaryMuted, modifier = Modifier.size(18.dp))
                Text("Wi-Fi · включено", style = MaterialTheme.typography.bodySmall, color = FinanceColors.OnPrimaryMuted)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Откройте в браузере компьютера", style = MaterialTheme.typography.bodySmall, color = FinanceColors.OnPrimaryMuted)
                Text(state.address.orEmpty(), color = FinanceColors.Card, fontSize = 24.sp, fontWeight = FontWeight.Medium)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Код входа", style = MaterialTheme.typography.bodySmall, color = FinanceColors.OnPrimaryMuted)
                Text(state.code.chunked(3).joinToString(" "), color = FinanceColors.Card, fontSize = 34.sp, fontWeight = FontWeight.Medium, letterSpacing = 6.sp)
            }
        }
    }
    WhiteCard(Modifier.fillMaxWidth()) {
        if (state.clients.isEmpty()) {
            Text("Пока никто не подключился", style = MaterialTheme.typography.bodyMedium, color = FinanceColors.Muted, modifier = Modifier.padding(16.dp))
        }
        state.clients.forEach { client ->
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                    Surface(color = FinanceColors.PrimarySoft, modifier = Modifier.fillMaxSize()) {}
                    Icon(Icons.Outlined.Laptop, contentDescription = null, tint = FinanceColors.OnPrimarySoft, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(client.name, style = MaterialTheme.typography.bodyLarge)
                    Text("Подключён · ${Dates.ago(client.lastSeen)}", style = MaterialTheme.typography.bodySmall, color = FinanceColors.Muted)
                }
                TextButton(onClick = { onDisconnect(client.token) }) { Text("Отключить", color = FinanceColors.Expense, fontWeight = FontWeight.Medium) }
            }
            HorizontalDivider(color = FinanceColors.Hairline)
        }
        Text(
            "Выключится сам через 30 минут без действий. Пока доступ включён, в шторке висит уведомление.",
            style = MaterialTheme.typography.bodySmall,
            color = FinanceColors.Muted,
            modifier = Modifier.padding(16.dp),
        )
    }
}
