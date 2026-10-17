package com.locctl.app.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SavedLocation(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val lat: Double,
    val lng: Double,
    val coordSystem: String = "gcj02",
    val isPreset: Boolean = false
)

object SavedLocationManager {
    private const val PREFS_NAME = "saved_locations_prefs"
    private const val KEY_CUSTOM_LOCATIONS = "custom_locations_json"

    private val presetLocations = listOf(
        SavedLocation(
            id = "preset_xihu",
            name = "杭州西湖",
            lat = 30.274602,
            lng = 120.211963,
            coordSystem = "gcj02",
            isPreset = true
        ),
        SavedLocation(
            id = "preset_waitan",
            name = "上海外滩",
            lat = 31.239693,
            lng = 121.490538,
            coordSystem = "gcj02",
            isPreset = true
        ),
        SavedLocation(
            id = "preset_tiananmen",
            name = "北京天安门",
            lat = 39.908722,
            lng = 116.397496,
            coordSystem = "gcj02",
            isPreset = true
        ),
        SavedLocation(
            id = "preset_shenzhen",
            name = "深圳市民中心",
            lat = 22.545538,
            lng = 114.054326,
            coordSystem = "gcj02",
            isPreset = true
        ),
        SavedLocation(
            id = "preset_guangzhoutower",
            name = "广州塔",
            lat = 23.106470,
            lng = 113.324460,
            coordSystem = "gcj02",
            isPreset = true
        ),
        SavedLocation(
            id = "preset_chunxilu",
            name = "成都春熙路",
            lat = 30.655820,
            lng = 104.081530,
            coordSystem = "gcj02",
            isPreset = true
        )
    )

    private val _locations = MutableStateFlow<List<SavedLocation>>(presetLocations)
    val locations: StateFlow<List<SavedLocation>> = _locations.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadLocations()
        }
    }

    private fun loadLocations() {
        val sp = prefs ?: return
        val jsonStr = sp.getString(KEY_CUSTOM_LOCATIONS, null)
        val customList = mutableListOf<SavedLocation>()
        if (!jsonStr.isNullOrEmpty()) {
            runCatching {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    customList.add(
                        SavedLocation(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            name = obj.getString("name"),
                            lat = obj.getDouble("lat"),
                            lng = obj.getDouble("lng"),
                            coordSystem = obj.optString("coordSystem", "gcj02"),
                            isPreset = false
                        )
                    )
                }
            }
        }
        _locations.value = customList + presetLocations
    }

    fun saveLocation(name: String, lat: Double, lng: Double, coordSystem: String = "gcj02") {
        val sp = prefs ?: return
        val currentCustom = _locations.value.filter { !it.isPreset }.toMutableList()
        val newLoc = SavedLocation(
            name = name.trim().ifEmpty { "未命名地址" },
            lat = lat,
            lng = lng,
            coordSystem = coordSystem,
            isPreset = false
        )
        currentCustom.add(0, newLoc)

        saveCustomListToPrefs(sp, currentCustom)
        _locations.value = currentCustom + presetLocations
    }

    fun deleteLocation(id: String) {
        val sp = prefs ?: return
        val currentCustom = _locations.value.filter { !it.isPreset && it.id != id }
        saveCustomListToPrefs(sp, currentCustom)
        _locations.value = currentCustom + presetLocations
    }

    private fun saveCustomListToPrefs(sp: SharedPreferences, list: List<SavedLocation>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("lat", item.lat)
                put("lng", item.lng)
                put("coordSystem", item.coordSystem)
            }
            array.put(obj)
        }
        sp.edit().putString(KEY_CUSTOM_LOCATIONS, array.toString()).apply()
    }
}
