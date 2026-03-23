package com.example.sossmsapp.iu.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.sossmsapp.databinding.ActivityRegisterBinding
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        binding.btnSendOtp.setOnClickListener {
            val number = binding.etPhoneNumber.text.toString().trim()

            if (number.length == 10) {
                val fullNumber = "+91$number"
                startPhoneNumberVerification(fullNumber)
            } else {
                Toast.makeText(this, "Please enter a valid 10-digit number", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startPhoneNumberVerification(phoneNumber: String) {
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)       // Phone number to verify
            .setTimeout(60L, TimeUnit.SECONDS) // Timeout and unit
            .setActivity(this)                 // Activity (for callback binding)
            .setCallbacks(callbacks)           // OnVerificationStateChangedCallbacks
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            // This occurs if the SMS code is instantly intercepted (Auto-verification)
            // For now, we just proceed to navigation logic or sign in
        }

        override fun onVerificationFailed(e: FirebaseException) {
            // Check if SHA-1 is missing or if the quota is exceeded
            Toast.makeText(this@RegisterActivity, "Verification Failed: ${e.message}", Toast.LENGTH_LONG).show()
        }

        override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
            // The SMS has been sent successfully. Move to the next screen.
            val intent = Intent(this@RegisterActivity, OtpVerificationActivity::class.java)
            intent.putExtra("VERIFICATION_ID", verificationId)
            intent.putExtra("PHONE_NUMBER", binding.etPhoneNumber.text.toString().trim())
            startActivity(intent)
        }
    }
}