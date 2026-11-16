package com.example.synchroscreenou.network.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

class NetworkPermissionManager(
    private val context: Context
) {

    fun hasLocalNetworkPermission(): Boolean {

        if (Build.VERSION.SDK_INT < 37) {
            return true
        }

        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_LOCAL_NETWORK
        ) == PackageManager.PERMISSION_GRANTED
    }
}