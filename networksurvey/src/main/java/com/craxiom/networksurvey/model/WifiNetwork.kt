package com.craxiom.networksurvey.model

import android.os.Parcel
import android.os.Parcelable
import com.craxiom.messaging.wifi.Standard
import com.craxiom.messaging.wifi.WifiBandwidth
import java.io.Serializable

data class WifiNetwork(
    val bssid: String,
    val signalStrength: Float?,
    val ssid: String,
    val frequency: Int?,
    val channel: Int?,
    val bandwidth: WifiBandwidth?,
    val encryptionType: String,
    val passpoint: Boolean?,
    val capabilities: String,
    val standard: Standard?,
) : Serializable, Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readValue(Float::class.java.classLoader) as? Float,
        parcel.readString() ?: "",
        parcel.readValue(Int::class.java.classLoader) as? Int,
        parcel.readValue(Int::class.java.classLoader) as? Int,
        WifiBandwidth.forNumber(parcel.readValue(Int::class.java.classLoader) as? Int ?: 0),
        parcel.readString() ?: "",
        parcel.readValue(Boolean::class.java.classLoader) as? Boolean,
        parcel.readString() ?: "",
        Standard.forNumber(parcel.readValue(Int::class.java.classLoader) as? Int ?: 0),

        )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(bssid)
        parcel.writeValue(signalStrength)
        parcel.writeString(ssid)
        parcel.writeValue(frequency)
        parcel.writeValue(channel)
        parcel.writeValue(bandwidth ?: WifiBandwidth.UNKNOWN)
        parcel.writeString(encryptionType)
        parcel.writeValue(passpoint)
        parcel.writeString(capabilities)
        parcel.writeValue(standard ?: Standard.UNKNOWN)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<WifiNetwork> {

        const val KEY: String = "wifiNetwork"

        override fun createFromParcel(parcel: Parcel): WifiNetwork {
            return WifiNetwork(parcel)
        }

        override fun newArray(size: Int): Array<WifiNetwork?> {
            return arrayOfNulls(size)
        }
    }
}
