package com.example.sossmsapp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.sossmsapp.auth.SessionManager
import com.example.sossmsapp.iu.auth.RegisterActivity

class SplashActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Initialize your existing SessionManager
        sessionManager = SessionManager(this)

        Handler(Looper.getMainLooper()).postDelayed({
            // Use the isLoggedIn() function from your SessionManager
            if (sessionManager.isLoggedIn()) {
                // SUCCESS: User is logged in, go to Home
                startActivity(Intent(this, HomeActivity::class.java))
            } else {
                // FAIL: No session found, go to Register
                startActivity(Intent(this, RegisterActivity::class.java))
            }
            finish()
        }, 2500)
    }
}