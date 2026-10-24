package com.familyfinance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.familyfinance.data.db.FinanceDatabase
import com.familyfinance.data.mmbak.AndroidMmbakSource
import com.familyfinance.data.repository.RoomImportRepository
import com.familyfinance.data.sync.LocalChanges
import com.familyfinance.domain.importer.mmbak.MmbakReader
import com.familyfinance.ui.common.Dates
import com.familyfinance.ui.common.MoneyFormatter
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.TimeZone
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Starts the real app (Hilt, Room, navigation) on an imported backup, walks through the main screens
 * and saves screenshots to build/screenshots. Skipped unless -Pmmbak.path is set.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppSmokeTest {

    @get:Rule val compose = createEmptyComposeRule()

    private val shots = File("build/screenshots").apply { mkdirs() }

    @Test fun `main screens work on imported data`() {
        val path = System.getProperty("mmbak.path").orEmpty()
        assumeTrue("mmbak.path not set", path.isNotEmpty() && File(path).isFile)
        // Money Manager stored dates as local midnight in UTC+5, where the backup was made.
        val zone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Yekaterinburg"))
        try {
            importBackup(File(path))
            walkThroughScreens()
        } finally {
            TimeZone.setDefault(zone)
        }
    }

    private fun walkThroughScreens() {

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitForIdle()

            // Month picker: February 2021 is the last full month of the backup (the design shows it too).
            compose.onNodeWithText(Dates.month(YearMonth.now())).performClick()
            repeat(YearMonth.now().year - 2021) {
                compose.onNodeWithContentDescription("Предыдущий год").performClick()
            }
            compose.onNodeWithText(Dates.monthName(2)).performClick()
            waitForText("Февраль 2021")
            waitForText(MoneyFormatter.format(7139876, "RUB"))
            scenario.shot("1-transactions")

            // Open an existing operation, change its amount, save.
            compose.onAllNodes(hasText("Питание › ", substring = true) and hasClickAction()).onFirst().performClick()
            waitForText("Операция")
            scenario.shot("2-edit-existing")
            typeAmount("1000")
            compose.onNodeWithText("Сохранить").performClick()
            waitForText(MoneyFormatter.format(100000, "RUB"))

            // New operation: quick category chip, "save and add another", then a second item.
            compose.onNodeWithContentDescription("Добавить операцию").performClick()
            waitForText("Новая операция")
            waitForText("Молочка")
            typeAmount("200+50,5")
            waitForText("= " + MoneyFormatter.format(25050, "RUB"))
            compose.onAllNodes(hasText("Молочка") and hasClickAction()).onFirst().performClick()
            scenario.shot("3-new-operation")
            compose.onNodeWithText("Сохранить и ещё").performClick()
            waitForText("Сохранено. Следующая позиция")
            compose.onNodeWithText("Питание › Молочка").assertExists()
            typeAmount("99,9")
            compose.onNodeWithText("Сохранить").performClick()
            waitForText("Февраль 2021")

            compose.onNodeWithText("Календарь").performClick()
            waitForText("Пн")
            compose.onNode(hasContentDescription("25 февраля", substring = true)).performClick()
            waitForText(Dates.operations(13))
            scenario.shot("4-calendar")
            compose.onNodeWithText("Открыть день").performClick()
            waitForText("четверг")

            compose.onNodeWithText("Статистика").performClick()
            waitForText("расходы")
            scenario.shot("5-stats")
            compose.onAllNodes(hasText("Питание") and hasClickAction()).onFirst().performClick()
            waitForText("Молочка")
            scenario.shot("6-stats-food")
            compose.onNodeWithContentDescription("Ко всем категориям").performClick()

            // A whole year: 2020 expenses straight from the backup (the test's own edits are in 2021 and today).
            compose.onNodeWithText("Год", useUnmergedTree = true).performClick()
            waitForText("2021")
            compose.onNodeWithContentDescription("Предыдущий период").performClick()
            waitForText("2020")
            waitForText(MoneyFormatter.format(101329667, "RUB"))
            waitForText("расходы\nза год")
            scenario.shot("6b-stats-year")

            // Custom period: "Всё время" covers every income in the backup.
            compose.onNodeWithText("Период", useUnmergedTree = true).performClick()
            waitForText("Свой период")
            compose.onNodeWithText("Всё время").performClick()
            waitForText("+" + MoneyFormatter.format(325713099, "RUB"))
            compose.onNodeWithText("Показать").performClick()
            waitForText(Dates.range(LocalDate.of(2018, 1, 1), LocalDate.now()))
            waitForText("расходы\nза период")
            scenario.shot("6c-stats-all-time")

            compose.onNodeWithText("Счета").performClick()
            waitForText("Активы")
            // Untouched by the edits above; must equal the Money Manager balance to the kopeck.
            waitForText(MoneyFormatter.format(-235, "RUB"))
            scenario.shot("7-accounts")

            compose.onNodeWithText("Ещё").performClick()
            waitForText("Папка Syncthing")
            compose.onNodeWithText("Не выбрана").assertExists()
            compose.onNodeWithText("Выбрать папку").assertExists()
            scenario.shot("8-more")
        }
    }

    /** Types on the amount keypad, after erasing what was there. */
    private fun typeAmount(keys: String) {
        val names = mapOf('+' to "Плюс", '-' to "Минус", '×' to "Умножить", '÷' to "Разделить", ',' to "Запятая")
        fun press(name: String) = compose.onNode(hasContentDescription(name) and hasClickAction()).performClick()
        repeat(12) { press("Стереть") }
        keys.forEach { press(names[it] ?: it.toString()) }
    }

    /** Room loads on its own threads, which waitForIdle() does not track. */
    private fun waitForText(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun importBackup(file: File) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Same file the app's DatabaseModule opens.
        val db = Room.databaseBuilder(context, FinanceDatabase::class.java, "finance.db").build()
        runBlocking {
            val bundle = AndroidMmbakSource(file).use { MmbakReader("test").read(it) }
            RoomImportRepository(db, LocalChanges(db)).apply(bundle)
        }
        db.close()
    }

    // captureToImage() never sees a frame under Robolectric, so the window is drawn by hand.
    private fun ActivityScenario<MainActivity>.shot(name: String) {
        compose.waitForIdle()
        onActivity { activity ->
            val view = activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(shots, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
