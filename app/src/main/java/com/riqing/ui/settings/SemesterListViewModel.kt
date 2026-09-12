package com.riqing.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riqing.core.data.repo.SemesterRepository
import com.riqing.core.data.repo.SemesterWithSlots
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SemesterListUiState(
    val loading: Boolean = true,
    val items: List<SemesterWithSlots> = emptyList(),
)

/** 学期卡片列表页：展示全部已保存的学期（含节次），并可切换当前学期。 */
@HiltViewModel
class SemesterListViewModel @Inject constructor(
    private val semesters: SemesterRepository,
) : ViewModel() {
    val ui: StateFlow<SemesterListUiState> = semesters.observeAllWithSlots()
        .map { SemesterListUiState(loading = false, items = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SemesterListUiState())

    /** 立即把某学期设为当前学期（其余自动取消）；课表/今日页随当前学期变化。 */
    fun setCurrent(id: String) {
        viewModelScope.launch { semesters.setCurrent(id) }
    }
}
