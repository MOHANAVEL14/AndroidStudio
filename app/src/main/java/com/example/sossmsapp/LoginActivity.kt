package com.example.sossmsapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize SharedPreferences
        val sharedPref = getSharedPreferences("GuardianPrefs", Context.MODE_PRIVATE)

        // AUTO-SKIP LOGIC: Check if mobile number already exists
        val savedPhone = sharedPref.getString("USER_PHONE", null)
        if (savedPhone != null) {
            navigateToEmergencyContact()
            return
        }

        setContentView(R.layout.activity_login)

        val etMobileNumber = findViewById<EditText>(R.id.etMobileNumber)
        val btnContinue = findViewById<Button>(R.id.btnContinue)

        btnContinue.setOnClickListener {
            val phoneNumber = etMobileNumber.text.toString().trim()

            // Validate: Minimum 10 digits
            if (phoneNumber.length >= 10) {
                // Save to SharedPreferences
                val editor = sharedPref.edit()
                editor.putString("USER_PHONE", phoneNumber)
                editor.apply()

                // Move to next screen
                navigateToEmergencyContact()
            } else {
                Toast.makeText(this, "Please enter a valid 10-digit number", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToEmergencyContact() {
        val intent = Intent(this, EmergencyContactActivity::class.java)
        startActivity(intent)
        finish() // Close LoginActivity so user cannot go back to it
    }
}