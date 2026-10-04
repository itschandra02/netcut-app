package com.itschandra.netcut

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.itschandra.netcut.ui.DashboardScreen
import com.itschandra.netcut.ui.theme.NetCutTheme
import com.itschandra.netcut.vm.DashboardViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: DashboardViewModel = viewModel()
            val dark by vm.darkMode.collectAsState()
            NetCutTheme(darkMode = dark) {
                DashboardScreen(vm = vm, dark = dark, onToggleTheme = { vm.toggleTheme() })
            }
        }
    }
}
