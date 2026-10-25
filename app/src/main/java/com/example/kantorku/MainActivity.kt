package com.example.kantorku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    private lateinit var state: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = AppState(this)
        setContent { MaterialTheme { App(state) } }
    }

    override fun onDestroy() {
        state.close()
        super.onDestroy()
    }
}

@Composable
fun App(state: AppState) {
    val labels = listOf("Berkas", "PDF", "Dokumen", "Lembar", "Kompres")
    Scaffold(
        bottomBar = {
            NavigationBar {
                labels.forEachIndexed { i, label ->
                    NavigationBarItem(
                        selected = state.tab == i,
                        onClick = { state.tab = i },
                        icon = { Text(label.take(1)) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (state.tab) {
                0 -> HomeScreen(state)
                1 -> PdfScreen(state)
                2 -> EditorScreen(state)
                3 -> SheetScreen(state)
                else -> CompressScreen(state)
            }
        }
    }
}
