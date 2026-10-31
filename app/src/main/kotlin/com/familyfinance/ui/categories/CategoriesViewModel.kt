package com.familyfinance.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyfinance.domain.model.Category
import com.familyfinance.domain.model.CategoryColors
import com.familyfinance.domain.model.CategoryType
import com.familyfinance.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class CategoryRow(val category: Category, val operations: Int)

data class TopCategoryUi(val row: CategoryRow, val subs: List<CategoryRow>)

/** The category sheet: what is being typed, and what the choices are. */
data class CategoryEditUi(
    val isNew: Boolean,
    val uid: String,
    val type: CategoryType,
    val name: String,
    val icon: String?,
    val parentUid: String?,
    val archived: Boolean,
    val operations: Int,
    val hasChildren: Boolean,
    val mergeTargetUid: String? = null,
    val nameError: Boolean = false,
    /** Picked colour to save; null keeps the automatic one. Only top-level categories have one. */
    val color: String? = null,
    /** What the tile shows: [color], or the automatic or parent's colour. */
    val shownColor: String = CategoryColors.PALETTE.last(),
) {
    val canDelete: Boolean get() = !isNew && operations == 0 && !hasChildren
    val canMerge: Boolean get() = !isNew && !hasChildren
}

data class CategoriesUiState(
    val type: CategoryType = CategoryType.EXPENSE,
    val counts: Map<CategoryType, Int> = emptyMap(),
    val tops: List<TopCategoryUi> = emptyList(),
    val hidden: List<CategoryRow> = emptyList(),
    /** Top-level categories of the shown type, for "Входит в". */
    val parents: List<Category> = emptyList(),
    /** Every visible category of the shown type, for "Объединить с". */
    val all: List<Category> = emptyList(),
    val edit: CategoryEditUi? = null,
)

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val repository: CategoryRepository,
) : ViewModel() {

    private val type = MutableStateFlow(CategoryType.EXPENSE)
    private val edit = MutableStateFlow<CategoryEditUi?>(null)

    val state: StateFlow<CategoriesUiState> = combine(
        repository.observeAll(), repository.observeUsage(), type, edit,
    ) { categories, usage, shownType, editing ->
        fun row(c: Category) = CategoryRow(c, usage[c.uid] ?: 0)
        val ofType = categories.filter { it.type == shownType }
        val visible = ofType.filter { !it.archived }
        val visibleUids = visible.map { it.uid }.toSet()
        val tops = visible.filter { it.parentUid == null || it.parentUid !in visibleUids }.sortedBy { it.sortOrder }
        val children = visible.filter { it.parentUid != null && it.parentUid in visibleUids }.groupBy { it.parentUid }
        CategoriesUiState(
            type = shownType,
            counts = categories.filter { !it.archived && it.parentUid == null }.groupingBy { it.type }.eachCount(),
            tops = tops.map { top ->
                // Busiest first inside a category: the ones used daily are on top.
                TopCategoryUi(row(top), children[top.uid].orEmpty().map(::row).sortedByDescending { it.operations })
            },
            hidden = ofType.filter { it.archived }.map(::row),
            parents = tops,
            all = visible,
            edit = editing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    fun showType(value: CategoryType) {
        type.value = value
    }

    fun openNew(parentUid: String? = null) {
        viewModelScope.launch {
            val all = repository.observeAll().first()
            // A new category takes a colour no other shows yet; a subcategory shows its parent's.
            val color = if (parentUid == null) CategoryColors.nextFree(all, type.value) else null
            edit.value = CategoryEditUi(
                isNew = true, uid = UUID.randomUUID().toString(), type = type.value, name = "", icon = null,
                parentUid = parentUid, archived = false, operations = 0, hasChildren = false,
                color = color, shownColor = color ?: CategoryColors.resolve(all)[parentUid] ?: CategoryColors.PALETTE.last(),
            )
        }
    }

    fun openEdit(uid: String) {
        viewModelScope.launch {
            val all = repository.observeAll().first()
            val category = all.firstOrNull { it.uid == uid } ?: return@launch
            edit.value = CategoryEditUi(
                isNew = false, uid = uid, type = category.type, name = category.name, icon = category.icon,
                parentUid = category.parentUid, archived = category.archived,
                operations = repository.observeUsage().first()[uid] ?: 0,
                hasChildren = all.any { it.parentUid == uid && !it.deleted },
                color = category.color, shownColor = CategoryColors.resolve(all)[uid] ?: CategoryColors.PALETTE.last(),
            )
        }
    }

    fun setName(value: String) = edit.update { it?.copy(name = value, nameError = false) }
    fun setIcon(value: String?) = edit.update { it?.copy(icon = value) }
    fun setColor(value: String) = edit.update { it?.copy(color = value, shownColor = value) }
    fun setParent(uid: String?) = edit.update { it?.copy(parentUid = uid) }
    fun setMergeTarget(uid: String?) = edit.update { it?.copy(mergeTargetUid = uid) }
    fun dismiss() = edit.update { null }

    fun save() {
        val e = edit.value ?: return
        if (e.name.isBlank()) {
            edit.update { it?.copy(nameError = true) }
            return
        }
        viewModelScope.launch {
            val all = repository.observeAll().first()
            val existing = all.firstOrNull { it.uid == e.uid }
            val order = existing?.sortOrder ?: ((all.maxOfOrNull { it.sortOrder } ?: 0) + 1)
            repository.save(
                (existing ?: Category(e.uid, null, "", e.type, order, updatedAt = 0, deviceId = ""))
                    .copy(
                        name = e.name.trim(), icon = e.icon, parentUid = if (e.hasChildren) null else e.parentUid,
                        // Subcategories show their parent's colour, whatever is stored.
                        color = if (e.parentUid == null || e.hasChildren) e.color else existing?.color,
                    ),
            )
            edit.value = null
        }
    }

    fun setHidden(hidden: Boolean) {
        val e = edit.value ?: return
        viewModelScope.launch {
            repository.observeAll().first().firstOrNull { it.uid == e.uid }?.let { repository.save(it.copy(archived = hidden)) }
            edit.value = null
        }
    }

    fun delete() {
        val e = edit.value ?: return
        viewModelScope.launch {
            if (repository.delete(e.uid)) edit.value = null
        }
    }

    fun merge() {
        val e = edit.value ?: return
        val target = e.mergeTargetUid ?: return
        viewModelScope.launch {
            repository.merge(e.uid, target)
            edit.value = null
        }
    }
}
