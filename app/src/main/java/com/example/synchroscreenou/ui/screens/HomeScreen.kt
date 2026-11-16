package com.example.synchroscreenou.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onCreateClick: () -> Unit,
    onJoinClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Synchroscreenou",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text("Turn multiple phones into one shared display.")

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = onCreateClick) {
            Text("Create Display")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(onClick = onJoinClick) {
            Text("Join Display")
        }
    }
}