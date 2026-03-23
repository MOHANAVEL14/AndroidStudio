package com.example.sossmsapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class ActiveAlertActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_active_alert)

        val btnSafe = findViewById<Button>(R.id.btnIAmSafe)
        
        btnSafe.setOnClickListener {
            // Signal ShakeService to stop tracking
            val safeIntent = Intent(this, ShakeService::class.java).apply {
                action = "ACTION_SAFE"
            }
            startService(safeIntent)
            
            // Close this warning screen and return to whatever was underneath
            finish()
        }
    }

    // Disable the back button to force the user to press 'I AM SAFE'
    override fun onBackPressed() {
        // Do nothing!
    }
}
