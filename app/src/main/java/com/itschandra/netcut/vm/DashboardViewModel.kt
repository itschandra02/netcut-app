package com.itschandra.netcut.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.itschandra.netcut.data.DashboardUiState
import com.itschandra.netcut.data.NetcutRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = NetcutRepository(app)

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    private val _darkMode = MutableStateFlow(false)
    val darkMode: StateFlow<Boolean> = _darkMode.asStateFlow()

    private var pollJob: Job? = null

    init {
        val prefs = app.getSharedPreferences("netcut", 0)
        _darkMode.value = prefs.getBoolean("dark", false)
        startPolling()
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                val s = try { repo.tick() } catch (e: Exception) {
                    _state.value.copy(loading = false, logs = listOf("error: ${e.message}"))
                }
                _state.value = s
                delay(1_000)
            }
        }
    }

    fun scan() = viewModelScope.launch {
        _state.value = _state.value.copy(scanning = true)
        repo.scanNow()
    }

    fun setBlock(ip: String, on: Boolean) = viewModelScope.launch { repo.setBlock(ip, on) }

    fun setLimit(ip: String, kbps: Int) = viewModelScope.launch { repo.setLimit(ip, kbps) }

    fun setMitm(on: Boolean) = viewModelScope.launch { repo.setMitm(on) }

    fun toggleTheme() {
        val next = !_darkMode.value
        _darkMode.value = next
        getApplication<Application>()
            .getSharedPreferences("netcut", 0)
            .edit().putBoolean("dark", next).apply()
    }
}
