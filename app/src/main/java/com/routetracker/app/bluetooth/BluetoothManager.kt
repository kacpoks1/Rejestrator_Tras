package com.routetracker.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager as AndroidBluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class BluetoothConnectionState {
    object Idle : BluetoothConnectionState()
    data class WaitingForDevice(val deviceAddress: String) : BluetoothConnectionState()
    data class Connected(val device: BluetoothDeviceInfo) : BluetoothConnectionState()
    object Disconnected : BluetoothConnectionState()
}

class BluetoothManager(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as AndroidBluetoothManager
        manager.adapter
    }

    private val _connectionState = MutableStateFlow<BluetoothConnectionState>(BluetoothConnectionState.Idle)
    val connectionState: StateFlow<BluetoothConnectionState> = _connectionState.asStateFlow()

    private var preferredDeviceAddress: String? = null

    private val bluetoothReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            val device = intent?.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
            
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    if (device.address == preferredDeviceAddress) {
                        _connectionState.value = BluetoothConnectionState.Connected(
                            BluetoothDeviceInfo(device.name ?: "Unknown", device.address)
                        )
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    if (device.address == preferredDeviceAddress) {
                        _connectionState.value = BluetoothConnectionState.Disconnected
                    }
                }
            }
        }
    }

    fun getPairedDevices(): List<BluetoothDeviceInfo> {
        try {
            return bluetoothAdapter?.bondedDevices?.map {
                BluetoothDeviceInfo(it.name ?: "Unknown", it.address)
            } ?: emptyList()
        } catch (e: SecurityException) {
            return emptyList()
        }
    }

    fun startWatching(deviceAddress: String) {
        preferredDeviceAddress = deviceAddress
        _connectionState.value = BluetoothConnectionState.WaitingForDevice(deviceAddress)
        
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        context.registerReceiver(bluetoothReceiver, filter)
    }

    fun stopWatching() {
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
        preferredDeviceAddress = null
        _connectionState.value = BluetoothConnectionState.Idle
    }

    fun release() {
        stopWatching()
    }

    /**
     * Inner class for AndroidManifest registration if needed, 
     * but here we use dynamic registration for easier StateFlow integration.
     * The prompt mentioned a receiver in Manifest, but also dynamic management.
     * I'll stick to dynamic for the StateFlow to work easily.
     */
}
