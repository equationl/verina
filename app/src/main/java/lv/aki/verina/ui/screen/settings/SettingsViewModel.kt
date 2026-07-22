package lv.aki.verina.ui.screen.settings

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lv.aki.verina.service.VerinaForegroundService

data class SettingsUiState(
    val isServiceRunning: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
            _state.value = SettingsUiState(
                isServiceRunning = isServiceRunning(),
                isBatteryOptimizationIgnored = pm.isIgnoringBatteryOptimizations(app.packageName)
            )
        }
    }

    fun toggleService(context: Context) {
        val intent = Intent(context, VerinaForegroundService::class.java)
        if (_state.value.isServiceRunning) {
            context.stopService(intent)
        } else {
            ContextCompat.startForegroundService(context, intent)
        }
        viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            refresh()
        }
    }

    fun requestBatteryOptimizationExemption(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun isServiceRunning(): Boolean {
        val app = getApplication<Application>()
        val manager = app.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == VerinaForegroundService::class.java.name }
    }
}
