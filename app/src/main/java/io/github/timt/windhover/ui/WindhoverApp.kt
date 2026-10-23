package io.github.timt.windhover.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun WindhoverApp() =
    MaterialTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
            ProbeScreen(modifier = Modifier.padding(padding))
        }
    }
