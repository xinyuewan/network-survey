package com.craxiom.networksurvey.model

data class Plmn(val mcc: Int, val mnc: Int) {

    /**
     * Returns true if both mcc and mnc are not 0, false otherwise.
     */
    fun isSet(): Boolean {
        return mcc != 0 || mnc != 0
    }

    override fun toString(): String {
        return if (mcc == 0 && mnc == 0) {
            "Not Set"
        } else {
            "$mcc-$mnc"
        }
    }
}