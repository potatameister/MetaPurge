package com.metapurge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.metapurge.app.ui.screens.MainScreen
import com.metapurge.app.ui.theme.DarkNavy
import com.metapurge.app.ui.theme.MetaPurgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContent {
            var isDarkMode by remember { mutableStateOf(true) }
            
            MetaPurgeTheme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = if (isDarkMode) DarkNavy else androidx.compose.ui.graphics.Color(0xFFF8FAFC)
                ) {
                    MainScreen(
                        isDarkMode = isDarkMode,
                        onToggleTheme = { isDarkMode = !isDarkMode }
                    )
                }
            }
        }
    }
}
