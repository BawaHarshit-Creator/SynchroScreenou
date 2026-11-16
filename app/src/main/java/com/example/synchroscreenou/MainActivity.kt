package com.example.synchroscreenou

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.synchroscreenou.network.permissions.NetworkPermissionManager
import com.example.synchroscreenou.ui.theme.SynchroscreenouTheme
import com.example.synchroscreenou.ui.screens.HomeScreen
import com.example.synchroscreenou.ui.screens.CreateDisplayScreen
import com.example.synchroscreenou.ui.screens.JoinDisplayScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SynchroscreenouTheme {

                var currentScreen by remember {
                    mutableStateOf("home")
                }

                var pendingDestination by remember {
                    mutableStateOf<String?>(null)
                }

                val permissionManager = remember {
                    NetworkPermissionManager(this@MainActivity)
                }

                val permissionLauncher =
                    rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { isGranted ->

                        if (isGranted) {
                            pendingDestination?.let {
                                currentScreen = it
                            }
                        }

                        pendingDestination = null
                    }

                fun openNetworkScreen(destination: String) {

                    if (permissionManager.hasLocalNetworkPermission()) {
                        currentScreen = destination
                    } else {
                        pendingDestination = destination

                        permissionLauncher.launch(
                            Manifest.permission.ACCESS_LOCAL_NETWORK
                        )
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->

                    when (currentScreen) {

                        "home" -> HomeScreen(
                            modifier = Modifier.padding(innerPadding),
                            onCreateClick = {
                                openNetworkScreen("create")
                            },
                            onJoinClick = {
                                openNetworkScreen("join")
                            }
                        )

                        "create" -> CreateDisplayScreen(
                            onBack = {
                                currentScreen = "home"
                            }
                        )

                        "join" -> JoinDisplayScreen(
                            onBack = {
                                currentScreen = "home"
                            }
                        )

                        else -> {
                            currentScreen = "home"
                        }
                    }
                }
            }
        }
    }
}