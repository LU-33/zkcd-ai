package com.example.aicreationassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.aicreationassistant.ui.navigation.AppNavGraph
import com.example.aicreationassistant.ui.theme.AiCreationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AiCreationTheme {
                AppNavGraph()
            }
        }
    }
}
