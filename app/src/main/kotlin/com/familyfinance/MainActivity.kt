package com.familyfinance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.familyfinance.data.sync.SyncManager
import com.familyfinance.ui.main.MainScreen
import com.familyfinance.ui.theme.FinanceTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var syncManager: SyncManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Sync while the app is visible; stops in the background and resumes on return.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { syncManager.runWhileVisible() }
        }
        setContent {
            FinanceTheme {
                MainScreen()
            }
        }
    }
}
