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
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.nbansal.grid.sheet.SheetViewModel
import com.nbansal.grid.ui.EditCardScreen
import com.nbansal.grid.ui.HomeScreen
import com.nbansal.grid.ui.SheetScreen
import com.nbansal.grid.ui.rememberPhotoPicker
import com.nbansal.grid.ui.theme.GridTheme
import com.nbansal.grid.update.UpdateSheet
import com.nbansal.grid.update.UpdateState
import com.nbansal.grid.update.UpdateViewModel

class MainActivity : ComponentActivity() {
    private val updateVm: UpdateViewModel by viewModels()
    private val sheetVm: SheetViewModel by viewModels()

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
                    val nav = rememberNavController()
                    NavHost(nav, startDestination = "home", modifier = Modifier.padding(it)) {
                        composable("home") {
                            val pick = rememberPhotoPicker { uris ->
                                sheetVm.setPhotos(uris)
                                nav.navigate("sheet")
                            }
                            HomeScreen(
                                checking = state is UpdateState.Checking,
                                onNewSheet = pick,
                                onCheckForUpdates = { updateVm.check(silent = false) },
                            )
                        }
                        composable("sheet") {
                            SheetScreen(
                                vm = sheetVm,
                                onBack = { nav.popBackStack() },
                                onEdit = { nav.navigate("edit/$it") },
                            )
                        }
                        composable("edit/{index}", arguments = listOf(navArgument("index") { type = NavType.IntType })) {
                            EditCardScreen(sheetVm, it.arguments?.getInt("index") ?: 0) { nav.popBackStack() }
                        }
                    }
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
