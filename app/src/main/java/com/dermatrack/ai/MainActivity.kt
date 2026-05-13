package com.dermatrack.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dermatrack.ai.ui.DermaTrackAppRoot
import com.dermatrack.ai.ui.theme.DermaTrackTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as DermaTrackApp).container
        setContent {
            DermaTrackTheme {
                Surface {
                    val viewModel: MainViewModel = viewModel(
                        factory = MainViewModel.factory(container),
                    )
                    DermaTrackAppRoot(viewModel = viewModel)
                }
            }
        }
    }
}
