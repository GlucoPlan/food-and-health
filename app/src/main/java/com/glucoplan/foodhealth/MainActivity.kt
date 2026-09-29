package com.glucoplan.foodhealth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glucoplan.foodhealth.ui.navigation.AppNavigation
import com.glucoplan.foodhealth.ui.theme.FoodHealthTheme
import com.glucoplan.foodhealth.update.UpdateRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var updateRepository: UpdateRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Проверка обновления при запуске, не при повороте экрана
        if (savedInstanceState == null) updateRepository.checkIfDue()
        setContent {
            FoodHealthTheme {
                val update by updateRepository.state.collectAsStateWithLifecycle()
                AppNavigation(settingsBadge = update.updateAvailable)
            }
        }
    }
}
