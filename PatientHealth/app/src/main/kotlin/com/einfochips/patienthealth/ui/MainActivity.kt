package com.einfochips.patienthealth.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PatientHealthTheme { AppNav() } }
    }
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onAdd = { nav.navigate("form") },
                onEdit = { id -> nav.navigate("form?patientId=$id") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable(
            "form?patientId={patientId}",
            arguments = listOf(navArgument("patientId") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { PatientFormScreen(hiltViewModel(), onClose = { nav.popBackStack() }) }
        composable("settings") { SettingsScreen(hiltViewModel(), onBack = { nav.popBackStack() }) }
    }
}
