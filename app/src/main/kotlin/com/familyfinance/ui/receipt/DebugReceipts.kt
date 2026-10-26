package com.familyfinance.ui.receipt

import kotlinx.coroutines.flow.MutableStateFlow

/** A receipt handed over by `adb` in debug builds, waiting for the operations screen to take it. */
object DebugReceipts {
    const val EXTRA = "receipt_qr"
    val pending = MutableStateFlow<String?>(null)
}
