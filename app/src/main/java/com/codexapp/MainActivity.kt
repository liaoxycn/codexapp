package com.codexapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.ui.Modifier
import com.codexapp.ui.app.CodexApp
import com.codexapp.ui.theme.CodexTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CodexTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CodexTheme.colors.background),
                    color = CodexTheme.colors.background
                ) {
                    CodexApp()
                }
            }
        }
    }
}
