package com.craxiom.networksurvey.ui.cellular.model

/**
 * Represents the possible sources of cell towers from the NS tower service.
 */
enum class TowerSource(val apiName: String, val displayName: String) {
    OpenCelliD("OpenCelliD", "OpenCelliD"),
    BTSearch("BTSearch", "BTSearch (Poland)"),
    //BeaconDB("BeaconDB"),
    ;

    companion object {
        // Get enum from string (case insensitive)
        fun fromValue(value: String?): TowerSource? {
            return entries.find { it.apiName.equals(value, ignoreCase = true) }
        }
    }
}