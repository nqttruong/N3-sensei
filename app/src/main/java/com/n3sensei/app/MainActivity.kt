package com.n3sensei.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.n3sensei.app.app.MainViewModel
import com.n3sensei.app.app.N3SenseiRoot
import com.n3sensei.app.app.di.appViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            N3SenseiRoot(viewModel = appViewModel { MainViewModel(userPreferencesRepository) })
        }
    }
}
