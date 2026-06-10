package com.lagradost.cloudstream3

object PrefKeys {
    const val JWT_TOKEN = "jwt_token"
    const val IS_LOGGED_IN = "isLoggedIn"
    const val REMEMBER = "rememberMe"

    // Canonical keys used by other code (refreshToken, etc.)
    const val USERNAME = "username"
    const val PASSWORD = "password"

    // Optional UI-only keys (kept for compatibility)
    const val SAVED_USERNAME = "saved_username"
    const val SAVED_PASSWORD = "saved_password"

    const val DEVICE_ID = "device_id"
}