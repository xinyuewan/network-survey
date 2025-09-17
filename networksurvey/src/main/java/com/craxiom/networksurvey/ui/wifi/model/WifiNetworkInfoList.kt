package com.craxiom.networksurvey.ui.wifi.model

import android.os.Parcelable
import com.craxiom.networksurvey.model.WifiRecordWrapper
import kotlinx.parcelize.Parcelize
import java.io.Serializable

@Parcelize
data class WifiNetworkInfoList(
    val networks: List<WifiRecordWrapper>,
) : Serializable, Parcelable
