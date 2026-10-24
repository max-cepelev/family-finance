package com.familyfinance.ui.more

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyfinance.data.mmbak.MmbakImporter
import com.familyfinance.domain.importer.ImportFormatException
import com.familyfinance.domain.importer.ImportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ImportState {
    data object Idle : ImportState
    data object Running : ImportState
    data class Done(val result: ImportResult) : ImportState
    data class Failed(val message: String) : ImportState
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importer: MmbakImporter,
) : ViewModel() {

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    fun import(uri: Uri) {
        if (_state.value == ImportState.Running) return
        _state.value = ImportState.Running
        viewModelScope.launch {
            _state.value = try {
                val result = importer.import {
                    context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
                }
                ImportState.Done(result)
            } catch (e: ImportFormatException) {
                ImportState.Failed(e.message ?: "Неверный формат файла")
            } catch (e: Exception) {
                ImportState.Failed("Ошибка импорта: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun dismiss() {
        if (_state.value != ImportState.Running) _state.value = ImportState.Idle
    }
}
