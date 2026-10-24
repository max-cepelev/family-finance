package com.familyfinance.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Current time for "2 минуты назад" texts, refreshed whenever [key] changes. Passing the sync state
 * refreshes it at least once a minute (every sync round), without a timer of its own: an endless
 * delay loop in composition never lets Compose tests become idle.
 */
@Composable
fun rememberNow(key: Any?): Long = remember(key) { System.currentTimeMillis() }
