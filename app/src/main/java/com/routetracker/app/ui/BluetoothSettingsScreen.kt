package com.routetracker.app.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.routetracker.app.RouteViewModel
import com.routetracker.app.bluetooth.BluetoothDeviceInfo

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun BluetoothSettingsScreen(
    viewModel: RouteViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val btEnabled by viewModel.isBtAutoStartEnabled.collectAsState()
    val savedDevice by viewModel.btPreferredDevice.collectAsState()
    val pairedDevices = remember { viewModel.getPairedBluetoothDevices() }
    
    var selectedDeviceAddress by remember { mutableStateOf(savedDevice?.address) }
    var selectedDeviceName by remember { mutableStateOf(savedDevice?.name) }

    val bluetoothConnectPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        rememberPermissionState(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bluetooth auto-start") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Auto-start recording",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Start recording when device connects")
                Switch(
                    checked = btEnabled,
                    onCheckedChange = { viewModel.setBtAutoStartEnabled(it) }
                )
            }

            if (btEnabled) {
                if (bluetoothConnectPermission?.status?.isGranted == false) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Bluetooth Permission Required",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "This feature needs Bluetooth permission to detect device connections.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Button(
                                onClick = { bluetoothConnectPermission.launchPermissionRequest() },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text("Grant permission")
                            }
                        }
                    }
                } else {
                    savedDevice?.let { device ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(device.name, fontWeight = FontWeight.Bold)
                                    Text(device.address, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    "Connected",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }

                    Text(
                        "Select device",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    if (pairedDevices.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("No paired devices found.")
                            Button(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text("Open Bluetooth Settings")
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(pairedDevices) { device ->
                                DeviceItem(
                                    device = device,
                                    isSelected = selectedDeviceAddress == device.address,
                                    onSelect = {
                                        selectedDeviceAddress = device.address
                                        selectedDeviceName = device.name
                                    }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            selectedDeviceAddress?.let { address ->
                                viewModel.saveBtDevice(address, selectedDeviceName ?: "Unknown")
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedDeviceAddress != null
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceItem(
    device: BluetoothDeviceInfo,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        onClick = onSelect,
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(device.name, fontWeight = FontWeight.Bold)
                Text(device.address, style = MaterialTheme.typography.bodySmall)
            }
            RadioButton(selected = isSelected, onClick = onSelect)
        }
    }
}
