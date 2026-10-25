package com.familyfinance.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * The text of an input field, owned by the composition. Feeding a field from a ViewModel flow lags one
 * frame behind typing, and fast input then lands in the wrong place ("2824" became "8242" on the phone).
 * The ViewModel still receives every change; [key] starts over, e.g. for another record.
 */
@Composable
fun rememberFieldText(key: Any?, initial: String): MutableState<String> = remember(key) { mutableStateOf(initial) }
