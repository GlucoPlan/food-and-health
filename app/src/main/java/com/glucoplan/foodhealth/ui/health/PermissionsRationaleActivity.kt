package com.glucoplan.foodhealth.ui.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.glucoplan.foodhealth.ui.theme.FoodHealthTheme

/** Health Connect показывает этот экран, когда спрашивает, зачем приложению доступ (ТЗ 15.5). */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FoodHealthTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.safeDrawingPadding().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Зачем доступ к Health Connect", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "«Еда и здоровье» читает из Health Connect только сон — время засыпания и пробуждения " +
                                "за последние 14 дней, — чтобы не вводить его вручную. Данные записываются владельцу " +
                                "этого телефона и синхронизируются с сервером семьи, как остальные замеры.",
                        )
                        Text(
                            "Приложение ничего не записывает в Health Connect и не читает другие данные. " +
                                "Доступ можно отозвать в настройках Health Connect в любой момент.",
                        )
                        Button(onClick = ::finish) { Text("Понятно") }
                    }
                }
            }
        }
    }
}
