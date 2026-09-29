package com.glucoplan.foodhealth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glucoplan.foodhealth.data.sync.SyncScheduler
import com.glucoplan.foodhealth.ui.navigation.AppNavigation
import com.glucoplan.foodhealth.ui.navigation.RootState
import com.glucoplan.foodhealth.ui.navigation.RootViewModel
import com.glucoplan.foodhealth.ui.onboarding.ConnectScreen
import com.glucoplan.foodhealth.ui.onboarding.DownloadScreen
import com.glucoplan.foodhealth.ui.onboarding.OwnerSelectScreen
import com.glucoplan.foodhealth.ui.theme.FoodHealthTheme
import com.glucoplan.foodhealth.update.UpdateRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var updateRepository: UpdateRepository
    @Inject lateinit var syncScheduler: SyncScheduler

    private val rootViewModel: RootViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Проверка обновления при запуске, не при повороте экрана
        if (savedInstanceState == null) {
            updateRepository.checkIfDue()
            // ТЗ 6: синхронизация при запуске
            syncScheduler.syncNow()
        }
        setContent {
            FoodHealthTheme {
                val root by rootViewModel.state.collectAsStateWithLifecycle()
                when (root) {
                    RootState.Loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                    RootState.Connect -> ConnectScreen()
                    RootState.Download -> DownloadScreen()
                    RootState.NeedsOwner -> OwnerSelectScreen()
                    RootState.Ready -> {
                        val update by updateRepository.state.collectAsStateWithLifecycle()
                        AppNavigation(settingsBadge = update.updateAvailable)
                    }
                }
            }
        }
    }
}
