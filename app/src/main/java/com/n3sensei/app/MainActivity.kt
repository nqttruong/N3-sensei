package com.n3sensei.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.n3sensei.app.app.N3SenseiApp
import com.n3sensei.app.core.designsystem.theme.N3SenseiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            N3SenseiTheme {
                N3SenseiApp()
            }
        }
    }
}
