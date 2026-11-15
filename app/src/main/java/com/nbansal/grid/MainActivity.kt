package com.nbansal.grid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nbansal.grid.ui.HomeScreen
import com.nbansal.grid.ui.theme.GridTheme
import com.nbansal.grid.update.UpdateSheet
import com.nbansal.grid.update.UpdateState
import com.nbansal.grid.update.UpdateViewModel

class MainActivity : ComponentActivity() {
    private val updateVm: UpdateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) updateVm.check(silent = true)
        setContent {
            GridTheme {
                val state by updateVm.state.collectAsStateWithLifecycle()
                val snackbar = remember { SnackbarHostState() }
                LaunchedEffect(Unit) {
                    updateVm.messages.collect { snackbar.showSnackbar(it) }
                }
                Scaffold(
                    containerColor = Color.Transparent,
                    snackbarHost = { SnackbarHost(snackbar) },
                ) {
                    HomeScreen(
                        checking = state is UpdateState.Checking,
                        onCheckForUpdates = { updateVm.check(silent = false) },
                    )
                }
                UpdateSheet(
                    state = state,
                    onInstall = updateVm::downloadAndInstall,
                    onRetryInstall = updateVm::install,
                    onDismiss = updateVm::dismiss,
                )
            }
        }
    }
}
