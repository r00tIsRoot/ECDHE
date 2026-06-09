package com.example.ecdhe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ecdhe.ui.screens.ECDHEScreen
import com.example.ecdhe.ui.theme.ECDHETheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ECDHETheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ECDHEScreen()
                }
            }
        }
    }
}
