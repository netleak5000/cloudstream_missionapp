package com.lagradost.cloudstream3.utils

import android.content.Context
import android.provider.Settings

object DeviceUtils {
    fun getDeviceId(context: Context?): String {
        if (context == null) {
            return "unknown_device" // ✅ Prevents NullPointerException
        }

        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            e.printStackTrace()
            "unknown_device"
        }
    }
}
