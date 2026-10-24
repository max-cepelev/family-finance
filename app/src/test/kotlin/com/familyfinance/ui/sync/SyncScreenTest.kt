package com.familyfinance.ui.sync

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.familyfinance.ui.theme.FinanceTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

/** The sync screen in its states, with screenshots in build/screenshots for comparing with the design. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SyncScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val zone = ZoneId.systemDefault()
    private val now = LocalDateTime.of(2026, 10, 24, 14, 34).atZone(zone).toInstant().toEpochMilli()
    private fun at(hour: Int, minute: Int) = LocalDateTime.of(2026, 10, 24, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val connected = SyncUiState(
        enabled = true,
        folderName = "Syncthing/Семейный бюджет",
        lastSuccessAt = now - 2 * 60_000,
        lastApplied = 3,
        logCount = 2,
        logBytes = 3_565_000,
        devices = listOf(
            DeviceUi("samsung SM-A546E", isThisPhone = true, lastChangeAt = at(14, 32)),
            DeviceUi("Xiaomi 13", isThisPhone = false, lastChangeAt = at(14, 5)),
        ),
    )

    private fun show(state: SyncUiState, onDisable: () -> Unit = {}) {
        compose.setContent {
            FinanceTheme { SyncContent(state, now, onClose = {}, onSyncNow = {}, onChangeFolder = {}, onDisable = onDisable) }
        }
        compose.waitForIdle()
    }

    @Test fun `connected state matches the design texts`() {
        show(connected)
        compose.onNodeWithText("Синхронизировано").assertExists()
        compose.onNodeWithText("2 минуты назад · получено 3 изменения").assertExists()
        compose.onNodeWithText("2 журнала · 3,4 МБ").assertExists()
        compose.onNodeWithText("Изменение сегодня в 14:05").assertExists()
        shot("sync-connected")
    }

    @Test fun `error and lonely device states`() {
        show(connected.copy(error = "Нет доступа к папке — выберите её заново", devices = connected.devices.take(1)))
        compose.onNodeWithText("Не удалось синхронизировать").assertExists()
        compose.onNodeWithText("Нет доступа к папке — выберите её заново").assertExists()
        compose.onNodeWithText("Второй телефон появится здесь после его первой синхронизации с этой папкой").assertExists()
        shot("sync-error")
    }

    @Test fun `disabling asks first`() {
        var disabled = 0
        show(connected, onDisable = { disabled++ })
        compose.onNodeWithText("Отключить синхронизацию").performClick()
        compose.onNodeWithText("Отключить синхронизацию?").assertExists()
        assertEquals(0, disabled)
        compose.onNodeWithText("Отключить").performClick()
        assertEquals(1, disabled)
    }

    @Test fun `card subtitle follows the state`() {
        assertEquals("Не выбрана", SyncTexts.cardSubtitle(SyncUiState(), now))
        assertEquals("Синхронизировано 2 минуты назад", SyncTexts.cardSubtitle(connected, now))
        assertTrue(SyncTexts.cardSubtitle(connected.copy(error = "Папка недоступна"), now).startsWith("Папка"))
        assertEquals("820 КБ", SyncTexts.bytes(820 * 1024))
        // Imported records carry their Money Manager time: an older year must be visible.
        val march2023 = LocalDateTime.of(2023, 3, 20, 21, 51).atZone(zone).toInstant().toEpochMilli()
        assertEquals("20 марта 2023 в 21:51", com.familyfinance.ui.common.Dates.moment(march2023, now, zone))
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File("build/screenshots").apply { mkdirs() }.resolve("$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
