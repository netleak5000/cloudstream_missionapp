package com.lagradost.cloudstream3

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

object AuthManager {
    private const val PREFS = "user_prefs"
    // Keys - keep these synchronized with your LoginActivity / MainActivity usage
    object Keys {
        const val JWT_TOKEN = "jwt_token"
        const val IS_LOGGED_IN = "isLoggedIn"
        const val REMEMBER = "rememberMe"
        const val USERNAME = "username"         // canonical
        const val PASSWORD = "password"         // canonical
        const val SAVED_USERNAME = "saved_username" // ui-saved
        const val SAVED_PASSWORD = "saved_password"
        const val DEVICE_ID = "device_id"
    }

    /**
     * Logout a user on server (reset device) then clear local session keys and return to LoginActivity.
     * - Does NOT clear saved credentials unless remember flag is false.
     * - Call from UI thread (it will post UI Toasts itself).
     */
    fun logoutUser(activity: Activity, builder: BottomSheetDialog? = null) {
        val sharedPreferences = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        // Try canonical username first, fallback to saved_username for compatibility
        val username =
            sharedPreferences.getString(Keys.USERNAME, null)
                ?: sharedPreferences.getString(Keys.SAVED_USERNAME, null)

        if (username.isNullOrEmpty()) {
            Toast.makeText(activity, "No user logged in!", Toast.LENGTH_SHORT).show()
            return
        }

        val logoutUrl =
            "https://netleak.nl/app/wp-json/jwt-auth/v1/admin-reset-device?token=resetdevice@47&username=$username"

        val request = Request.Builder()
            .url(logoutUrl)
            .post("".toRequestBody(null)) // Empty body
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                activity.runOnUiThread {
                    Toast.makeText(activity, "Logout failed: ${e.message}", Toast.LENGTH_SHORT)
                        .show()
                }
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                activity.runOnUiThread {
                    if (response.isSuccessful) {
                        // Clear only auth/session related keys (keep saved credentials if REMEMBER = true)
                        val remember = sharedPreferences.getBoolean(Keys.REMEMBER, false)
                        val editor = sharedPreferences.edit()
                        editor.remove(Keys.JWT_TOKEN)
                        editor.remove(Keys.DEVICE_ID)
                        editor.putBoolean(Keys.IS_LOGGED_IN, false)

                        if (!remember) {
                            // If user didn't choose remember, also remove stored credentials
                            editor.remove(Keys.USERNAME)
                            editor.remove(Keys.PASSWORD)
                            editor.remove(Keys.SAVED_USERNAME)
                            editor.remove(Keys.SAVED_PASSWORD)
                            editor.putBoolean(Keys.REMEMBER, false)
                        }

                        editor.apply()

                        Toast.makeText(activity, "Logged out successfully!", Toast.LENGTH_SHORT)
                            .show()

                        // Close the bottom sheet if provided
                        builder?.dismiss()

                        // Redirect to Login Screen (clear back stack)
                        val intent = Intent(activity, LoginActivity::class.java)
                        intent.flags =
                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        activity.startActivity(intent)
                    } else {
                        Toast.makeText(activity, "Logout failed. Try again!", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }
        })
    }

    /**
     * Clear local session immediately without a server call.
     * Use this for forced logout or offline mode.
     */
    fun clearLocalSession(context: Context, preserveRememberedCredentials: Boolean = true) {
        val sharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.remove(Keys.JWT_TOKEN)
        editor.remove(Keys.DEVICE_ID)
        editor.putBoolean(Keys.IS_LOGGED_IN, false)

        if (!preserveRememberedCredentials) {
            editor.remove(Keys.USERNAME)
            editor.remove(Keys.PASSWORD)
            editor.remove(Keys.SAVED_USERNAME)
            editor.remove(Keys.SAVED_PASSWORD)
            editor.putBoolean(Keys.REMEMBER, false)
        }
        editor.apply()
    }
}