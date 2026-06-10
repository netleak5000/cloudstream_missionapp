package com.lagradost.cloudstream3

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.hbb20.CountryCodePicker
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import android.widget.Toast
import android.view.View


class RegisterActivity : AppCompatActivity() {

    private val REGISTER_URL = "https://netleak.nl/app/wp-json/wp/v2/users/register"
    private val CHECK_PHONE_URL = "https://netleak.nl/app/wp-json/netleak/v1/check-phone"
    private lateinit var httpClient: OkHttpClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        fetchRegisterNotice() // 🔹 Call added here to show the registration notice

        httpClient = OkHttpClient()

        val usernameInput = findViewById<EditText>(R.id.etRegisterUsername)
        val emailInput = findViewById<EditText>(R.id.etRegisterEmail)
        val passwordInput = findViewById<EditText>(R.id.etRegisterPassword)
        val phoneInput = findViewById<EditText>(R.id.etRegisterPhone)
        val ccp = findViewById<CountryCodePicker>(R.id.ccp)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        val subscriptionButton = findViewById<Button>(R.id.btnSubscription)

        subscriptionButton.setOnClickListener {
            val intent = Intent(this, SubscriptionActivity::class.java)
            startActivity(intent)
        }


        ccp.registerCarrierNumberEditText(phoneInput)

        // Button click listener for the registration
        btnRegister.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            val fullPhone = ccp.fullNumberWithPlus

            if (username.isEmpty() || email.isEmpty() || password.isEmpty() || fullPhone.isEmpty()) {
                Toast.makeText(this, "All fields required", Toast.LENGTH_SHORT).show()
            } else {
                // Check if registration is allowed before proceeding
                checkRegistrationAllowed { allowed ->
                    if (allowed) {
                        checkPhoneExists(fullPhone) { exists ->
                            if (exists) {
                                Toast.makeText(this, "Phone already registered", Toast.LENGTH_SHORT).show()
                            } else {
                                registerUser(username, email, password, fullPhone)
                            }
                        }
                    } else {
                        Toast.makeText(this, "Registration is currently disabled", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun fetchRegisterNotice() {
        val url = "https://netleak.nl/app/wp-json/wp/v2/register-notice"

        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Handle failure
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.let {
                    val json = JSONObject(it.string())
                    val enabled = json.optBoolean("enabled", false)
                    val message = json.optString("message", "")

                    if (enabled && message.isNotBlank()) {
                        runOnUiThread {
                            val tvMessage = findViewById<TextView>(R.id.tvMessage)
                            tvMessage.text = message
                            tvMessage.visibility = View.VISIBLE
                        }
                    }
                }
            }
        })
    }


    // Function to check if registration is allowed
    private fun checkRegistrationAllowed(callback: (Boolean) -> Unit) {
        val url = "https://netleak.nl/app/wp-json/app/v1/registration_status"

        val request = Request.Builder().url(url).build()
        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@RegisterActivity, "Failed to check status", Toast.LENGTH_SHORT).show()
                    callback(false)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                val allowed = JSONObject(body).optBoolean("allow_registration", false)
                runOnUiThread {
                    callback(allowed)
                }
            }
        })
    }

    // Function to check if the phone number is already registered
    private fun checkPhoneExists(phone: String, callback: (Boolean) -> Unit) {
        val json = JSONObject().put("phone", phone)
        val body =
            json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder().url(CHECK_PHONE_URL).post(body).build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { callback(false) }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.use {
                    val result = JSONObject(it.string())
                    val exists = result.optBoolean("exists", false)
                    runOnUiThread { callback(exists) }
                }
            }
        })
    }

    // Function to perform the user registration
    private fun registerUser(username: String, email: String, password: String, phone: String) {
        val json = JSONObject().apply {
            put("username", username)
            put("email", email)
            put("password", password)
            put("phone_number", phone)
        }

        val requestBody =
            json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder().url(REGISTER_URL).post(requestBody).build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(
                        this@RegisterActivity,
                        "Network error: ${e.localizedMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseText = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(
                            this@RegisterActivity,
                            "Registration successful!",
                            Toast.LENGTH_SHORT
                        ).show()
                        startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                        finish()
                    } else {
                        val jsonError = try {
                            JSONObject(responseText ?: "{}").optString(
                                "message",
                                "Registration failed"
                            )
                        } catch (e: Exception) {
                            "Registration failed"
                        }
                        Toast.makeText(this@RegisterActivity, jsonError, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}
