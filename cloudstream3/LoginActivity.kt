package com.lagradost.cloudstream3

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class LoginActivity : AppCompatActivity() {

    private val WORDPRESS_LOGIN_URL = "https://netleak.nl/app/wp-json/jwt-auth/v1/token"
    private lateinit var httpClient: OkHttpClient
    private lateinit var deviceId: String

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            networkInfo?.isConnectedOrConnecting == true
        }
    }

    // Save token, login flag, optionally credentials and device_id
// Put this inside LoginActivity (or update your existing method)
    private fun saveLoginState(
        token: String,
        username: String,
        password: String,
        rememberMe: Boolean,
        deviceIdToSave: String
    ) {
        val sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        // Always save token and logged-in flag and device id
        editor.putString("jwt_token", token)
        editor.putBoolean("isLoggedIn", true)
        editor.putString("device_id", deviceIdToSave)

        // Always save username (needed for logout / server reset endpoint)
        editor.putString("username", username)

        if (rememberMe) {
            // Only save password when the user opted into remembering credentials
            editor.putString("password", password)

            // optional UI prefill keys, keep them in sync if you use them:
            editor.putString("saved_username", username)
            editor.putString("saved_password", password)
            editor.putBoolean("rememberMe", true)
        } else {
            // make sure we do NOT keep password when not remembering
            editor.remove("password")
            editor.remove("saved_password") // optional cleanup
            editor.putBoolean("rememberMe", false)
        }

        editor.apply()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // fetch device id safely (your DeviceUtils call or fallback)
        deviceId = try {
            com.lagradost.cloudstream3.utils.DeviceUtils.getDeviceId(this) ?: Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        }
        Log.d("DeviceID", "DeviceId used: $deviceId")

        // Always show login screen (do not auto-navigate away)
        setContentView(R.layout.activity_login)

        httpClient = OkHttpClient()

        val gifImageView: ImageView = findViewById(R.id.ivLogo)
        Glide.with(this).asGif().load(R.drawable.app_logo).into(gifImageView)

        val usernameInput = findViewById<EditText>(R.id.etUsername)
        val passwordInput = findViewById<EditText>(R.id.etPassword)
        val loginButton = findViewById<Button>(R.id.btnLogin)
        val useSavedButton = findViewById<Button?>(R.id.btnUseSaved)
        val rememberMeCheckBox = findViewById<CheckBox>(R.id.rememberMeCheckBox)

        val sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val isRememberMeChecked = sharedPreferences.getBoolean(PrefKeys.REMEMBER, false)
        rememberMeCheckBox.isChecked = isRememberMeChecked

        val savedUsername = sharedPreferences.getString(PrefKeys.SAVED_USERNAME, "") ?: ""
        val savedPassword = sharedPreferences.getString(PrefKeys.SAVED_PASSWORD, "") ?: ""
        rememberMeCheckBox.isChecked = true

        if (isRememberMeChecked) {
            usernameInput.setText(savedUsername)
            passwordInput.setText(savedPassword)
        }

        // Setup "Use saved credentials" button: show only when saved credentials exist
        useSavedButton?.let { btn ->
            if (savedUsername.isNotEmpty() && savedPassword.isNotEmpty()) {
                btn.visibility = android.view.View.VISIBLE
                btn.setOnClickListener {
                    // One-tap login using saved credentials
                    loginButton.isEnabled = false
                    loginUser(savedUsername, savedPassword, true)
                }
            } else {
                btn.visibility = android.view.View.GONE
            }
        }

        val registerText = findViewById<TextView>(R.id.btnRegister)
        registerText.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        loginButton.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            val rememberMe = rememberMeCheckBox.isChecked
            val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            currentFocus?.let {
                inputMethodManager.hideSoftInputFromWindow(it.windowToken, 0)
            }

            if (username.isNotEmpty() && password.isNotEmpty()) {
                loginUser(username, password, rememberMe)
            } else {
                Toast.makeText(this, "Please enter username and password", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loginUser(username: String, password: String, rememberMe: Boolean) {
        if (!isNetworkAvailable()) {
            runOnUiThread {
                Toast.makeText(this@LoginActivity, "No internet connection", Toast.LENGTH_SHORT).show()
            }
            return
        }

        runOnUiThread {
            findViewById<Button>(R.id.btnLogin).isEnabled = false
        }

        val currentDeviceId = deviceId

        val json = JSONObject().apply {
            put("username", username)
            put("password", password)
            put("device_id", currentDeviceId)
        }

        val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(WORDPRESS_LOGIN_URL)
            .post(requestBody)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@LoginActivity, "Network error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    findViewById<Button>(R.id.btnLogin).isEnabled = true
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.use { body ->
                    val responseBody = body.string()
                    val jsonResponse = JSONObject(responseBody)

                    runOnUiThread {
                        if (response.isSuccessful && jsonResponse.has("token")) {
                            val token = jsonResponse.getString("token")

                            // Verify plan before proceeding
                            val planCheckRequest = Request.Builder()
                                .url("https://netleak.nl/app/wp-json/netleak/v1/check-plan")
                                .post(
                                    JSONObject().put("token", token)
                                        .toString()
                                        .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                                )
                                .build()

                            OkHttpClient().newCall(planCheckRequest).enqueue(object : Callback {
                                override fun onFailure(call: Call, e: IOException) {
                                    runOnUiThread {
                                        Toast.makeText(this@LoginActivity, "Failed to verify plan. Try again.", Toast.LENGTH_SHORT).show()
                                        findViewById<Button>(R.id.btnLogin).isEnabled = true
                                    }
                                }

                                override fun onResponse(call: Call, response: Response) {
                                    response.body?.use { planBody ->
                                        val planResponse = JSONObject(planBody.string())
                                        val isActive = planResponse.optBoolean("active", false)

                                        runOnUiThread {
                                            if (isActive) {
                                                saveLoginState(token, username, password, rememberMe, currentDeviceId)
                                                Toast.makeText(this@LoginActivity, "Login successful!", Toast.LENGTH_SHORT).show()
                                                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                                                finish()
                                            } else {
                                                Toast.makeText(this@LoginActivity, "Your plan is inactive.", Toast.LENGTH_SHORT).show()
                                                findViewById<Button>(R.id.btnLogin).isEnabled = true
                                            }
                                        }
                                    }
                                }
                            })
                        } else {
                            val rawErrorMessage = jsonResponse.optString("message", "Username or Password Invalid")
                            val cleanErrorMessage = rawErrorMessage.replace(Regex("<.*?>"), "").trim()
                            Toast.makeText(this@LoginActivity, "Login failed: $cleanErrorMessage", Toast.LENGTH_SHORT).show()
                            findViewById<Button>(R.id.btnLogin).isEnabled = true
                        }
                    }
                }
            }
        })
    }
}