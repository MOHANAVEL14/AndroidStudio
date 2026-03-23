package com.example.sossmsapp.iu.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.example.sossmsapp.HomeActivity // Replace with your actual Home Activity name

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val auth = FirebaseAuth.getInstance()

        // Check if user is already logged in
        if (auth.currentUser != null) {
            // User is logged in, go to safety screen
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        } else {
            // No user found, go to Registration
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }
    }
}