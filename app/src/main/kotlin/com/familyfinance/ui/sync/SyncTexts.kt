package com.familyfinance.ui.sync

import com.familyfinance.ui.common.Dates
import java.util.Locale

/** Texts shared by the sync screen and the card on «Ещё». */
object SyncTexts {
    fun title(state: SyncUiState): String = when {
        state.error != null -> "Не удалось синхронизировать"
        state.running && state.lastSuccessAt == null -> "Синхронизация…"
        state.lastSuccessAt != null -> "Синхронизировано"
        else -> "Ещё не синхронизировано"
    }

    /** "2 минуты назад · получено 3 изменения". */
    fun details(state: SyncUiState, now: Long): String? {
        state.error?.let { return it }
        val at = state.lastSuccessAt ?: return null
        val applied = state.lastApplied?.takeIf { it > 0 }
            ?.let { " · получено " + Dates.plural(it, "изменение", "изменения", "изменений") }
        return Dates.ago(at, now) + applied.orEmpty()
    }

    /** Subtitle under «Папка Syncthing» on the card. */
    fun cardSubtitle(state: SyncUiState, now: Long): String = when {
        !state.enabled -> "Не выбрана"
        state.error != null -> state.error
        state.lastSuccessAt != null -> "Синхронизировано " + Dates.ago(state.lastSuccessAt, now)
        else -> title(state)
    }

    /** "2 журнала · 3,4 МБ". */
    fun folderDetails(state: SyncUiState): String? {
        val count = state.logCount ?: return null
        val logs = Dates.plural(count, "журнал", "журнала", "журналов")
        return state.logBytes?.let { "$logs · ${bytes(it)}" } ?: logs
    }

    fun bytes(value: Long): String {
        val ru = Locale.forLanguageTag("ru-RU")
        return when {
            value >= 1024 * 1024 -> String.format(ru, "%.1f МБ", value / 1024.0 / 1024.0)
            value >= 1024 -> "${value / 1024} КБ"
            else -> "$value Б"
        }
    }
}
