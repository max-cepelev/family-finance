package com.familyfinance.ui.main

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyfinance.domain.model.CategoryColors
import com.familyfinance.domain.repository.CategoryRepository
import com.familyfinance.ui.common.categoryColor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Colours of all categories for [com.familyfinance.ui.common.LocalCategoryColors]. */
@HiltViewModel
class CategoryColorsViewModel @Inject constructor(categories: CategoryRepository) : ViewModel() {
    val colors: StateFlow<Map<String, Color>> = categories.observeAll()
        .map { list -> CategoryColors.resolve(list).mapValues { categoryColor(it.value) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
}
