package com.n3sensei.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.n3sensei.app.app.MainViewModel
import com.n3sensei.app.app.N3SenseiRoot
import com.n3sensei.app.app.di.appViewModel
import com.n3sensei.app.app.widget.StudyWordWidget
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            N3SenseiRoot(viewModel = appViewModel { MainViewModel(userPreferencesRepository, contentImportRepository, reminderScheduler, databaseRecoveryRepository) })
        }
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app is when progress may have changed: refresh the home-screen widget.
        lifecycleScope.launch { StudyWordWidget.updateAllSizes(applicationContext) }
    }
}
