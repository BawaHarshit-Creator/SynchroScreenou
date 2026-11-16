package com.example.synchroscreenou.network.discovery

import android.content.Context
import android.net.wifi.WifiManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

class NetworkDiscoveryManager(
    context: Context,
    private val onDisplayFound: (DiscoveredDisplay) -> Unit = {}
) {

    private val appContext = context.applicationContext

    private val nsdManager =
        appContext.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val wifiManager =
        appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val multicastLock = wifiManager.createMulticastLock("SynchroscreenMulticastLock").apply {
        setReferenceCounted(true)
    }

    private val serviceType = "_synchroscreen._tcp."

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    // ADVERTISING CALLBACK

    private val internalRegistrationListener =
        object : NsdManager.RegistrationListener {

            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                Log.d("NetworkDiscovery", "Registered: ${serviceInfo.serviceName}")
            }

            override fun onRegistrationFailed(
                serviceInfo: NsdServiceInfo,
                errorCode: Int
            ) {
                Log.e("NetworkDiscovery", "Registration failed: $errorCode")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Log.d("NetworkDiscovery", "Service unregistered")
            }

            override fun onUnregistrationFailed(
                serviceInfo: NsdServiceInfo,
                errorCode: Int
            ) {
                Log.e("NetworkDiscovery", "Unregistration failed: $errorCode")
            }
        }

    // DISCOVERY CALLBACK

    private val internalDiscoveryListener =
        object : NsdManager.DiscoveryListener {

            override fun onDiscoveryStarted(serviceType: String) {
                Log.d("NetworkDiscovery", "Discovery started")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {

                Log.d(
                    "NetworkDiscovery",
                    "Service found: ${serviceInfo.serviceName}"
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    resolveModern(serviceInfo)
                } else {
                    resolveLegacy(serviceInfo)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(
                    "NetworkDiscovery",
                    "Service lost: ${serviceInfo.serviceName}"
                )
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d("NetworkDiscovery", "Discovery stopped")
            }

            override fun onStartDiscoveryFailed(
                serviceType: String,
                errorCode: Int
            ) {
                Log.e("NetworkDiscovery", "Start failed: $errorCode")
                nsdManager.stopServiceDiscovery(this)
            }

            override fun onStopDiscoveryFailed(
                serviceType: String,
                errorCode: Int
            ) {
                Log.e("NetworkDiscovery", "Stop failed: $errorCode")
            }
        }

    // MODERN RESOLUTION: API 34+

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun resolveModern(serviceInfo: NsdServiceInfo) {

        nsdManager.registerServiceInfoCallback(
            serviceInfo,
            ContextCompat.getMainExecutor(appContext),

            object : NsdManager.ServiceInfoCallback {

                override fun onServiceUpdated(
                    updatedServiceInfo: NsdServiceInfo
                ) {

                    val addresses = updatedServiceInfo.hostAddresses

                    Log.d(
                        "NetworkDiscovery",
                        "Resolved device: ${updatedServiceInfo.serviceName}"
                    )

                    addresses.forEach { address ->
                        val ip = address.hostAddress
                        if (ip != null) {
                            val display = DiscoveredDisplay(
                                name = updatedServiceInfo.serviceName,
                                ipAddress = ip,
                                port = updatedServiceInfo.port
                            )
                            onDisplayFound(display)
                            Log.d(
                                "NetworkDiscovery",
                                "Display discovered: $display"
                            )
                        }
                    }
                }

                override fun onServiceLost() {
                    Log.d("NetworkDiscovery", "Resolved service lost")
                }

                override fun onServiceInfoCallbackRegistrationFailed(
                    errorCode: Int
                ) {
                    Log.e(
                        "NetworkDiscovery",
                        "Resolution failed: $errorCode"
                    )
                }

                override fun onServiceInfoCallbackUnregistered() {
                    Log.d(
                        "NetworkDiscovery",
                        "Service callback unregistered"
                    )
                }
            }
        )
    }

    // LEGACY RESOLUTION: API 26–33

    @Suppress("DEPRECATION")
    private fun resolveLegacy(serviceInfo: NsdServiceInfo) {

        nsdManager.resolveService(
            serviceInfo,
            object : NsdManager.ResolveListener {

                override fun onResolveFailed(
                    serviceInfo: NsdServiceInfo,
                    errorCode: Int
                ) {
                    Log.e(
                        "NetworkDiscovery",
                        "Legacy resolution failed: $errorCode"
                    )
                }

                override fun onServiceResolved(
                    resolvedInfo: NsdServiceInfo
                ) {
                    val address = resolvedInfo.host?.hostAddress

                    if (address != null) {

                        val display = DiscoveredDisplay(
                            name = resolvedInfo.serviceName,
                            ipAddress = address,
                            port = resolvedInfo.port
                        )

                        onDisplayFound(display)

                        Log.d(
                            "NetworkDiscovery",
                            "Display discovered: $display"
                        )
                    }
                }
            }
        )
    }

    // START ADVERTISING

    fun startAdvertising(port: Int) {

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "Synchroscreenou"
            serviceType = this@NetworkDiscoveryManager.serviceType
            this.port = port
        }

        val listener = internalRegistrationListener
        registrationListener = listener

        nsdManager.registerService(
            serviceInfo,
            NsdManager.PROTOCOL_DNS_SD,
            listener
        )
    }

    // STOP ADVERTISING

    fun stopAdvertising() {

        registrationListener?.let {
            try {
                nsdManager.unregisterService(it)
            } catch (e: Exception) {
                Log.e("NetworkDiscovery", "Unregister error", e)
            } finally {
                registrationListener = null
            }
        }
    }

    // START DISCOVERY

    fun startDiscovery() {

        if (discoveryListener != null) return

        try {
            multicastLock.acquire()
        } catch (e: Exception) {
            Log.e("NetworkDiscovery", "Failed to acquire multicast lock", e)
        }

        val listener = internalDiscoveryListener
        discoveryListener = listener

        nsdManager.discoverServices(
            serviceType,
            NsdManager.PROTOCOL_DNS_SD,
            listener
        )
    }

    // STOP DISCOVERY

    fun stopDiscovery() {

        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.e("NetworkDiscovery", "Stop discovery error", e)
            } finally {
                discoveryListener = null
            }
        }

        try {
            if (multicastLock.isHeld) {
                multicastLock.release()
            }
        } catch (e: Exception) {
            Log.e("NetworkDiscovery", "Failed to release multicast lock", e)
        }
    }
}
