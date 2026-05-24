package com.example

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.ui.AlarmViewModel
import com.example.ui.DashboardScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AlarmViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Inspect and process alarm intents on cold-start
        checkAlarmIntent(intent)

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DashboardScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Keep standard activity state synced with new incoming intents
        setIntent(intent)
        checkAlarmIntent(intent)
    }

    private fun checkAlarmIntent(intent: Intent?) {
        if (intent == null) return
        val isFiring = intent.getBooleanExtra("ALARM_FIRING", false)
        Log.d("MainActivity", "checkAlarmIntent: IS_FIRING = $isFiring")
        if (isFiring) {
            val alarmId = intent.getIntExtra("ALARM_ID", -1)
            val label = intent.getStringExtra("ALARM_LABEL") ?: "Core Alert"
            val shakesRequired = intent.getIntExtra("ALARM_SHAKES", 20)

            Log.d("MainActivity", "Alarm receiver intent recognized! Starting active takeover screen.")
            viewModel.startFiringSequence(
                label = label,
                shakesRequired = shakesRequired,
                alarmId = if (alarmId != -1) alarmId else null
            )
        }
    }
}
