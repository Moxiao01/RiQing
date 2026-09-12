package com.riqing.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riqing.core.datastore.SettingsDataStore
import com.riqing.core.datastore.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsThemeViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsDataStore.settings
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    /** 选中即写入并全局生效，无需保存按钮。 */
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsDataStore.setThemeMode(mode)
        }
    }
}
