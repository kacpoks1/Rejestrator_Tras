package com.routetracker.app.bluetooth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bluetooth_prefs")

data class BluetoothDeviceInfo(val name: String, val address: String)

class BluetoothPreferences(private val context: Context) {

    companion object {
        private val PREF_BT_ENABLED = booleanPreferencesKey("bt_enabled")
        private val PREF_BT_DEVICE_ADDRESS = stringPreferencesKey("bt_device_address")
        private val PREF_BT_DEVICE_NAME = stringPreferencesKey("bt_device_name")
    }

    val btEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PREF_BT_ENABLED] ?: false
    }

    val btDevice: Flow<BluetoothDeviceInfo?> = context.dataStore.data.map { preferences ->
        val address = preferences[PREF_BT_DEVICE_ADDRESS]
        val name = preferences[PREF_BT_DEVICE_NAME]
        if (address != null && name != null) {
            BluetoothDeviceInfo(name, address)
        } else {
            null
        }
    }

    suspend fun setBtEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PREF_BT_ENABLED] = enabled
        }
    }

    suspend fun setBtDevice(address: String, name: String) {
        context.dataStore.edit { preferences ->
            preferences[PREF_BT_DEVICE_ADDRESS] = address
            preferences[PREF_BT_DEVICE_NAME] = name
        }
    }
}
