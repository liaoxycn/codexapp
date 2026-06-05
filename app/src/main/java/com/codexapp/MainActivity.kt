package com.codexapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.ui.Modifier
import com.codexapp.debug.AgentDebugBridge
import com.codexapp.ui.app.CodexApp
import com.codexapp.ui.state.HomeViewModel
import com.codexapp.ui.theme.CodexTheme

class MainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by viewModels()
    private var agentDebugBridge: AgentDebugBridge? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        agentDebugBridge = AgentDebugBridge.startIfDebuggable(
            context = this,
            viewModel = homeViewModel
        )
        setContent {
            CodexTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CodexTheme.colors.background),
                    color = CodexTheme.colors.background
                ) {
                    CodexApp(viewModel = homeViewModel)
                }
            }
        }
    }

    override fun onDestroy() {
        agentDebugBridge?.stop()
        agentDebugBridge = null
        super.onDestroy()
    }
}
