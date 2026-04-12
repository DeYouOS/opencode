package com.opencode.remote.ui.nav

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.opencode.remote.ui.screen.ConnectScreen
import com.opencode.remote.ui.screen.DashboardScreen
import com.opencode.remote.ui.screen.PermissionScreen
import com.opencode.remote.ui.screen.SessionScreen
import com.opencode.remote.viewmodel.RemoteViewModel

@Composable
fun AppNavigation(vm: RemoteViewModel = viewModel()) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "dashboard") {
        composable("connect") {
            ConnectScreen(vm) { nav.popBackStack() }
        }
        composable("dashboard") {
            DashboardScreen(vm,
                onSession = { nav.navigate("session/$it") },
                onPermissions = { nav.navigate("permissions") },
                onSettings = { nav.navigate("connect") }
            )
        }
        composable("session/{id}") { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            SessionScreen(vm, id, { nav.popBackStack() }) { nav.navigate("session/$it") }
        }
        composable("permissions") {
            PermissionScreen(vm) { nav.popBackStack() }
        }
    }
}
