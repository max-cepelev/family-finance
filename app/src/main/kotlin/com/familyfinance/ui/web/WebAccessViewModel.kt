package com.familyfinance.ui.web

import androidx.lifecycle.ViewModel
import com.familyfinance.web.WebAccess
import com.familyfinance.web.WebAccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class WebAccessViewModel @Inject constructor(private val access: WebAccess) : ViewModel() {
    val state: StateFlow<WebAccessState> = access.state

    fun start() = access.start()
    fun stop() = access.stop()
    fun disconnect(token: String) = access.disconnect(token)
}
