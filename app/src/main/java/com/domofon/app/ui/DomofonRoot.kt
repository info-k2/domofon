package com.domofon.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun DomofonRoot(viewModel: DomofonViewModel = viewModel()) {
    val nav = rememberNavController()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val updateStatus by viewModel.updateStatus.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        NavHost(
            navController = nav,
            startDestination = "live",
            modifier = Modifier.padding(padding),
        ) {
            composable("live") {
                LiveScreen(
                    settings = settings,
                    updateStatus = updateStatus,
                    onOpenDoor = viewModel::openDoor,
                    onUseLocal = viewModel::setUseLocalRtsp,
                    onOpenSettings = { nav.navigate("settings") },
                    onUpdate = viewModel::updateApp,
                )
            }
            composable("settings") {
                SettingsScreen(
                    current = settings,
                    onSave = viewModel::saveSettings,
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}
